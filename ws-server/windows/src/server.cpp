#include <uwebsockets/App.h>
#include <uwebsockets/Loop.h>

#include <jwt-cpp/jwt.h>
#include <jwt-cpp/traits/nlohmann-json/defaults.h>
#include <nlohmann/json.hpp>
#include <sw/redis++/redis++.h>

extern "C" {
#include <rabbitmq-c/amqp.h>
#include <rabbitmq-c/framing.h>
#include <rabbitmq-c/tcp_socket.h>
}

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cctype>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <fstream>
#include <functional>
#include <iomanip>
#include <iostream>
#include <limits>
#include <filesystem>
#include <memory>
#include <mutex>
#include <optional>
#include <random>
#include <sstream>
#include <string>
#include <string_view>
#include <thread>
#include <unordered_map>
#include <utility>
#include <vector>

using json = nlohmann::json;

namespace {

struct ServerConfig {
    bool useSsl{false};
    std::string sslKeyFile;
    std::string sslCertFile;
    unsigned short port{8080};
    std::string authPublicPem;
    bool authDevMode{false};
    std::chrono::seconds jwtLeeway{60};
    unsigned idleTimeout{120};
    std::size_t maxPayload{1024 * 1024};
    std::size_t maxBackpressure{64 * 1024};
    bool wsWelcome{false};
    bool wsAck{false};
    bool debugWs{true};
    bool compressionShared{false};
    std::string rabbitUrl;
    std::string rabbitExchange{"ws_ingress"};
    std::string rabbitRoutingKey{"client.event"};
    std::string redisUrl;
};

struct JwtPayload {
    std::string userId;
    std::string appId;
    std::string deviceId;
    std::string ip;
    std::uint64_t exp{0};
};

std::string toLower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return value;
}

std::string trim(std::string value) {
    auto isSpace = [](unsigned char c) { return std::isspace(c) != 0; };
    value.erase(value.begin(), std::find_if(value.begin(), value.end(), [&](unsigned char c) { return !isSpace(c); }));
    value.erase(std::find_if(value.rbegin(), value.rend(), [&](unsigned char c) { return !isSpace(c); }).base(), value.end());
    return value;
}

std::string getEnv(const std::string& name, const std::string& fallback = {}) {
    const char* value = std::getenv(name.c_str());
    if (!value) return fallback;
    return std::string(value);
}

bool envFlag(const std::string& name, bool fallback = false) {
    std::string value = toLower(trim(getEnv(name)));
    if (value.empty()) return fallback;
    return value == "1" || value == "true" || value == "on" || value == "yes";
}

std::uint64_t envUInt64(const std::string& name, std::uint64_t fallback) {
    std::string value = trim(getEnv(name));
    if (value.empty()) return fallback;
    try {
        return static_cast<std::uint64_t>(std::stoull(value));
    } catch (...) {
        return fallback;
    }
}

unsigned short envPort(const std::string& name, unsigned short fallback) {
    auto val = envUInt64(name, fallback);
    if (val > 65535ull) val = fallback;
    return static_cast<unsigned short>(val);
}

std::string statusString(int code) {
    switch (code) {
    case 200: return "200 OK";
    case 201: return "201 Created";
    case 202: return "202 Accepted";
    case 204: return "204 No Content";
    case 400: return "400 Bad Request";
    case 401: return "401 Unauthorized";
    case 404: return "404 Not Found";
    case 405: return "405 Method Not Allowed";
    case 409: return "409 Conflict";
    case 413: return "413 Payload Too Large";
    case 500: return "500 Internal Server Error";
    default: {
        std::ostringstream oss;
        oss << code << " OK";
        return oss.str();
    }
    }
}

std::uint64_t nowMs() {
    return static_cast<std::uint64_t>(std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count());
}

std::string randomHex(std::size_t bytes = 16) {
    static thread_local std::mt19937_64 rng{std::random_device{}()};
    std::uniform_int_distribution<int> dist(0, 255);
    std::string out;
    out.reserve(bytes * 2);
    static const char* hex = "0123456789abcdef";
    for (std::size_t i = 0; i < bytes; ++i) {
        int v = dist(rng);
        out.push_back(hex[(v >> 4) & 0xF]);
        out.push_back(hex[v & 0xF]);
    }
    return out;
}

std::string replaceEscapedNewlines(std::string value) {
    std::string::size_type pos = 0;
    while ((pos = value.find("\\n", pos)) != std::string::npos) {
        value.replace(pos, 2, "\n");
    }
    return value;
}

std::optional<std::string> readFile(const std::string& path) {
    if (path.empty()) return std::nullopt;
    std::ifstream file(path, std::ios::binary);
    if (!file.is_open()) return std::nullopt;
    std::ostringstream oss;
    oss << file.rdbuf();
    return oss.str();
}

// Environment & paths helpers
#ifdef _WIN32
#  ifndef NOMINMAX
#    define NOMINMAX
#  endif
#  include <windows.h>
#endif

std::string executableDir() {
#ifdef _WIN32
    char buf[MAX_PATH];
    DWORD len = GetModuleFileNameA(nullptr, buf, MAX_PATH);
    if (len > 0) {
        return std::filesystem::path(std::string(buf, len)).parent_path().string();
    }
    return std::filesystem::current_path().string();
#else
    return std::filesystem::current_path().string();
#endif
}

void setEnvVar(const std::string& key, const std::string& value) {
#ifdef _WIN32
    _putenv_s(key.c_str(), value.c_str());
#else
    setenv(key.c_str(), value.c_str(), 1);
#endif
}

bool loadDotEnvAt(const std::filesystem::path& file) {
    if (!std::filesystem::exists(file)) return false;
    std::ifstream in(file, std::ios::binary);
    if (!in.is_open()) return false;
    std::string line;
    while (std::getline(in, line)) {
        // strip CR and trim
        if (!line.empty() && line.back() == '\r') line.pop_back();
        auto s = trim(line);
        if (s.empty() || s.rfind('#', 0) == 0 || s.rfind(';', 0) == 0) continue;
        auto eq = s.find('=');
        if (eq == std::string::npos || eq == 0) continue;
        std::string key = trim(s.substr(0, eq));
        std::string val = trim(s.substr(eq + 1));
        if (val.size() >= 2 && ((val.front() == '"' && val.back() == '"') || (val.front() == '\'' && val.back() == '\''))) {
            val = val.substr(1, val.size() - 2);
        }
        setEnvVar(key, val);
        // Special-case PEM file: resolve relative to .env location if not absolute
        if (key == "AUTH_PUBLIC_PEM_FILE" && !val.empty()) {
            std::filesystem::path p(val);
            if (!p.is_absolute()) {
                auto candidate = (file.parent_path() / p).lexically_normal();
                if (std::filesystem::exists(candidate)) {
                    setEnvVar("AUTH_PUBLIC_PEM_FILE", candidate.string());
                }
            }
        }
    }
    return true;
}

void loadDefaultDotEnv() {
    // Try CWD .env then executable dir .env
    std::filesystem::path cwd = std::filesystem::current_path() / ".env";
    std::filesystem::path exe = std::filesystem::path(executableDir()) / ".env";
    bool loaded = false;
    loaded = loadDotEnvAt(cwd) || loadDotEnvAt(exe);
    (void)loaded; // best-effort
}

std::string base64Decode(const std::string& input) {
    static const int decodeTable[256] = {
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,62,-1,-1,-1,63,
        52,53,54,55,56,57,58,59,60,61,-1,-1,-1, 0,-1,-1,
        -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,
        15,16,17,18,19,20,21,22,23,24,25,-1,-1,-1,-1,-1,
        -1,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,
        41,42,43,44,45,46,47,48,49,50,51,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
    };
    std::string out;
    out.reserve((input.size() * 3) / 4);
    int val = 0;
    int bits = -8;
    for (unsigned char c : input) {
        if (std::isspace(c)) continue;
        if (c == '=') break;
        int decoded = decodeTable[c];
        if (decoded < 0) return {};
        val = (val << 6) | decoded;
        bits += 6;
        if (bits >= 0) {
            out.push_back(static_cast<char>((val >> bits) & 0xFF));
            bits -= 8;
        }
    }
    return out;
}

int hexValue(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

std::optional<std::string> percentDecode(std::string_view input) {
    std::string result;
    result.reserve(input.size());
    for (std::size_t i = 0; i < input.size(); ++i) {
        char c = input[i];
        if (c == '%') {
            if (i + 2 >= input.size()) return std::nullopt;
            int hi = hexValue(input[i + 1]);
            int lo = hexValue(input[i + 2]);
            if (hi < 0 || lo < 0) return std::nullopt;
            result.push_back(static_cast<char>((hi << 4) | lo));
            i += 2;
        } else {
            result.push_back(c);
        }
    }
    return result;
}

// Normalize textual IP so comparisons between JWT claim and client IP are robust
// - Map IPv6 loopback (::1) to 127.0.0.1
// - Map IPv6-mapped IPv4 (e.g., ::ffff:127.0.0.1 or ::ffff:7f00:0001) to dotted IPv4
std::string normalizeIp(std::string ip) {
    ip = trim(ip);
    if (ip.empty()) return ip;
    std::string lower = toLower(ip);
    // Handle IPv6 loopback
    if (lower == "::1" || lower == "0:0:0:0:0:0:0:1") {
        return std::string("127.0.0.1");
    }
    // Handle IPv6-mapped form ::ffff:a.b.c.d
    auto ffffPos = lower.rfind("::ffff:");
    if (ffffPos != std::string::npos) {
        std::string tail = lower.substr(ffffPos + 7); // after ::ffff:
        // If already dotted, return as-is (strip brackets/spaces already done by trim)
        if (tail.find('.') != std::string::npos) return tail;
        // Else expect two hex hextets like 7f00:0001 (=> 127.0.0.1)
        auto colon = tail.find(':');
        if (colon != std::string::npos) {
            auto h1s = tail.substr(0, colon);
            auto h2s = tail.substr(colon + 1);
            try {
                unsigned h1 = h1s.empty() ? 0u : static_cast<unsigned>(std::stoul(h1s, nullptr, 16));
                unsigned h2 = h2s.empty() ? 0u : static_cast<unsigned>(std::stoul(h2s, nullptr, 16));
                unsigned a = (h1 >> 8) & 0xFFu;
                unsigned b = h1 & 0xFFu;
                unsigned c = (h2 >> 8) & 0xFFu;
                unsigned d = h2 & 0xFFu;
                std::ostringstream oss;
                oss << a << '.' << b << '.' << c << '.' << d;
                return oss.str();
            } catch (...) {
                // fallthrough to return original ip
            }
        }
        return tail; // best-effort
    }
    // Handle verbose IPv6 mapped like 0000:...:ffff:xxxx:yyyy
    auto pos = lower.find(":ffff:");
    if (pos != std::string::npos) {
        std::string tail = lower.substr(pos + 6);
        if (tail.find('.') != std::string::npos) return tail;
        auto colon = tail.find(':');
        if (colon != std::string::npos) {
            auto h1s = tail.substr(0, colon);
            auto h2s = tail.substr(colon + 1);
            try {
                unsigned h1 = h1s.empty() ? 0u : static_cast<unsigned>(std::stoul(h1s, nullptr, 16));
                unsigned h2 = h2s.empty() ? 0u : static_cast<unsigned>(std::stoul(h2s, nullptr, 16));
                unsigned a = (h1 >> 8) & 0xFFu;
                unsigned b = h1 & 0xFFu;
                unsigned c = (h2 >> 8) & 0xFFu;
                unsigned d = h2 & 0xFFu;
                std::ostringstream oss;
                oss << a << '.' << b << '.' << c << '.' << d;
                return oss.str();
            } catch (...) {
            }
        }
        return tail;
    }
    return ip;
}

struct AmqpUri {
    std::string host{"localhost"};
    int port{5672};
    std::string vhost{"/"};
    std::string user{"guest"};
    std::string password{"guest"};
    bool ssl{false};
};

std::optional<AmqpUri> parseAmqpUri(const std::string& uri) {
    AmqpUri parsed;
    std::string input = trim(uri);
    if (input.empty()) return parsed;

    if (input.rfind("amqps://", 0) == 0) {
        parsed.ssl = true;
        input.erase(0, 8);
    } else if (input.rfind("amqp://", 0) == 0) {
        input.erase(0, 7);
    }

    auto queryPos = input.find('?');
    if (queryPos != std::string::npos) {
        input.erase(queryPos);
    }

    std::string hostPort = input;
    auto atPos = input.find('@');
    if (atPos != std::string::npos) {
        std::string userInfo = input.substr(0, atPos);
        hostPort = input.substr(atPos + 1);

        auto colonPos = userInfo.find(':');
        std::string userEnc = colonPos == std::string::npos ? userInfo : userInfo.substr(0, colonPos);
        std::string passEnc = colonPos == std::string::npos ? std::string() : userInfo.substr(colonPos + 1);

        if (!userEnc.empty()) {
            auto decoded = percentDecode(userEnc);
            if (!decoded) return std::nullopt;
            parsed.user = *decoded;
        }
        if (!passEnc.empty()) {
            auto decoded = percentDecode(passEnc);
            if (!decoded) return std::nullopt;
            parsed.password = *decoded;
        } else if (colonPos != std::string::npos) {
            parsed.password.clear();
        }
    }

    std::string vhostPart;
    auto slashPos = hostPort.find('/');
    if (slashPos != std::string::npos) {
        vhostPart = hostPort.substr(slashPos + 1);
        hostPort.erase(slashPos);
    }
    if (!vhostPart.empty()) {
        auto decoded = percentDecode(vhostPart);
        if (!decoded) return std::nullopt;
        parsed.vhost = decoded->empty() ? std::string{"/"} : *decoded;
    }

    std::string hostPart = trim(hostPort);
    if (!hostPart.empty()) {
        if (hostPart.front() == '[') {
            auto close = hostPart.find(']');
            if (close == std::string::npos) return std::nullopt;
            parsed.host = hostPart.substr(1, close - 1);
            if (close + 1 < hostPart.size()) {
                if (hostPart[close + 1] != ':') return std::nullopt;
                std::string portStr = hostPart.substr(close + 2);
                if (!portStr.empty()) {
                    try {
                        parsed.port = std::stoi(portStr);
                    } catch (...) {
                        return std::nullopt;
                    }
                }
            }
        } else {
            auto colonPos = hostPart.rfind(':');
            if (colonPos != std::string::npos && hostPart.find(':') == colonPos) {
                std::string portStr = hostPart.substr(colonPos + 1);
                std::string hostOnly = hostPart.substr(0, colonPos);
                if (!hostOnly.empty()) parsed.host = hostOnly;
                if (!portStr.empty()) {
                    try {
                        parsed.port = std::stoi(portStr);
                    } catch (...) {
                        return std::nullopt;
                    }
                }
            } else {
                parsed.host = hostPart;
            }
        }
    }

    if (parsed.port <= 0 || parsed.port > 65535) return std::nullopt;
    if (parsed.host.empty()) parsed.host = "localhost";
    if (parsed.vhost.empty()) parsed.vhost = "/";
    return parsed;
}

std::string loadPublicPem() {
    // Try from ENV file path, and if relative, resolve against executable dir
    {
        std::string pemPath = trim(getEnv("AUTH_PUBLIC_PEM_FILE"));
        if (!pemPath.empty()) {
            if (auto s = readFile(pemPath); s) return *s;
            std::filesystem::path rel = std::filesystem::path(executableDir()) / pemPath;
            if (auto s2 = readFile(rel.string()); s2) return *s2;
        }
    }
    std::string b64 = trim(getEnv("AUTH_PUBLIC_PEM_B64"));
    if (!b64.empty()) {
        auto decoded = base64Decode(b64);
        if (!decoded.empty()) return decoded;
    }
    std::string inlinePem = replaceEscapedNewlines(getEnv("AUTH_PUBLIC_PEM"));
    return inlinePem;
}

std::string buildDeviceKey(const std::string& appId, const std::string& deviceId) {
    std::string key = "ws:dev:";
    key += appId.empty() ? "na" : appId;
    key.push_back(':');
    key += deviceId.empty() ? "na" : deviceId;
    return key;
}

namespace {

std::string rpcReplyToString(const amqp_rpc_reply_t& reply) {
    switch (reply.reply_type) {
    case AMQP_RESPONSE_NORMAL:
        return "normal";
    case AMQP_RESPONSE_NONE:
        return "missing reply";
    case AMQP_RESPONSE_LIBRARY_EXCEPTION:
        return reply.library_error ? amqp_error_string2(reply.library_error) : "library exception";
    case AMQP_RESPONSE_SERVER_EXCEPTION:
        if (reply.reply.id == AMQP_CONNECTION_CLOSE_METHOD) {
            const auto* m = static_cast<const amqp_connection_close_t*>(reply.reply.decoded);
            std::ostringstream oss;
            oss << "connection close reply-code=" << m->reply_code << " reply-text=";
            if (m->reply_text.bytes && m->reply_text.len) {
                oss.write(static_cast<const char*>(m->reply_text.bytes), static_cast<std::streamsize>(m->reply_text.len));
            }
            return oss.str();
        }
        if (reply.reply.id == AMQP_CHANNEL_CLOSE_METHOD) {
            const auto* m = static_cast<const amqp_channel_close_t*>(reply.reply.decoded);
            std::ostringstream oss;
            oss << "channel close reply-code=" << m->reply_code << " reply-text=";
            if (m->reply_text.bytes && m->reply_text.len) {
                oss.write(static_cast<const char*>(m->reply_text.bytes), static_cast<std::streamsize>(m->reply_text.len));
            }
            return oss.str();
        }
        return "server exception";
    default:
        return "unknown reply";
    }
}

} // namespace

class RabbitPublisher {
public:
    RabbitPublisher(std::string uri, std::string exchange)
        : uri_(std::move(uri)), exchange_(std::move(exchange)) {}

    ~RabbitPublisher() { close(); }

    bool publish(const std::string& routingKey, const json& body) {
        std::string payload = body.dump();
        std::lock_guard<std::mutex> lock(mutex_);
        if (!ensureConnectionLocked()) return false;

        amqp_basic_properties_t props;
        std::memset(&props, 0, sizeof(props));
        props._flags = AMQP_BASIC_CONTENT_TYPE_FLAG | AMQP_BASIC_DELIVERY_MODE_FLAG;
        props.content_type = amqp_cstring_bytes("application/json");
        props.delivery_mode = 2; // persistent

        amqp_bytes_t bodyBytes{payload.size(), const_cast<char*>(payload.data())};
        int publishStatus = amqp_basic_publish(
            connection_,
            channel_,
            amqp_cstring_bytes(exchange_.c_str()),
            amqp_cstring_bytes(routingKey.c_str()),
            false,
            false,
            &props,
            bodyBytes);

        if (publishStatus != AMQP_STATUS_OK) {
            std::cerr << "[AMQP] basic_publish failed: " << amqp_error_string2(publishStatus) << "\n";
            resetConnectionLocked();
            return false;
        }
        return true;
    }

    void close() {
    std::lock_guard<std::mutex> lock(mutex_);
        resetConnectionLocked();
    }

private:
    bool ensureConnectionLocked() {
        if (connection_) return true;

        amqp_connection_state_t conn = amqp_new_connection();
        if (!conn) {
            std::cerr << "[AMQP] new_connection failed" << "\n";
            return false;
        }

        auto parsed = parseAmqpUri(uri_);
        if (!parsed) {
            std::cerr << "[AMQP] parse URL failed for " << uri_ << "\n";
            amqp_destroy_connection(conn);
            return false;
        }

        if (parsed->ssl) {
            std::cerr << "[AMQP] SSL AMQP URLs are not supported in this build" << "\n";
            amqp_destroy_connection(conn);
            return false;
        }

        std::string host = parsed->host;
        int port = parsed->port;
        std::string vhost = parsed->vhost;
        std::string user = parsed->user;
        std::string password = parsed->password;

        amqp_socket_t* socket = amqp_tcp_socket_new(conn);
        if (!socket) {
            std::cerr << "[AMQP] tcp_socket_new failed" << "\n";
            amqp_destroy_connection(conn);
            return false;
        }

        if (amqp_socket_open(socket, host.c_str(), port) != AMQP_STATUS_OK) {
            std::cerr << "[AMQP] socket open failed for " << host << ":" << port << "\n";
            amqp_destroy_connection(conn);
            return false;
        }

        auto loginReply = amqp_login(conn, vhost.c_str(), 0, 131072, 0, AMQP_SASL_METHOD_PLAIN, user.c_str(), password.c_str());
        if (!checkReply("login", loginReply)) {
            amqp_destroy_connection(conn);
            return false;
        }

        amqp_channel_open(conn, channel_);
        if (!checkReply("channel_open", amqp_get_rpc_reply(conn))) {
            amqp_destroy_connection(conn);
            return false;
        }

        amqp_exchange_declare(conn,
            channel_,
            amqp_cstring_bytes(exchange_.c_str()),
            amqp_cstring_bytes("topic"),
            false,
            true,
            false,
            false,
            amqp_empty_table);
        if (!checkReply("exchange_declare", amqp_get_rpc_reply(conn))) {
            amqp_destroy_connection(conn);
            return false;
        }

        connection_ = conn;
        socket_ = socket;
        return true;
    }

    bool checkReply(const char* context, const amqp_rpc_reply_t& reply) {
        if (reply.reply_type == AMQP_RESPONSE_NORMAL) return true;

        std::cerr << "[AMQP] " << context << " failed: " << rpcReplyToString(reply) << "\n";
        return false;
    }

    void resetConnectionLocked() {
        if (connection_) {
            amqp_connection_close(connection_, AMQP_REPLY_SUCCESS);
            amqp_destroy_connection(connection_);
            connection_ = nullptr;
        }
        socket_ = nullptr;
    }

    std::mutex mutex_;
    std::string uri_;
    std::string exchange_;
    amqp_connection_state_t connection_{nullptr};
    amqp_socket_t* socket_{nullptr};
    int channel_{1};
};

class RedisManager {
public:
    using PushHandler = std::function<void(const std::string&, const json&)>;
    using KickHandler = std::function<void(const std::string&, const std::string&)>;

    explicit RedisManager(const std::string& url) {
        if (url.empty()) return;
        try {
            redis_ = std::make_unique<sw::redis::Redis>(url);
            pub_ = std::make_unique<sw::redis::Redis>(url);
            sub_ = std::make_unique<sw::redis::Redis>(url);
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] init error: " << e.what() << "\n";
            redis_.reset();
            pub_.reset();
            sub_.reset();
        }
    }

    ~RedisManager() { stop(); }

    bool enabled() const { return static_cast<bool>(redis_); }

    bool acquireDeviceLock(const std::string& key, const std::string& value, std::chrono::seconds ttl) {
        if (!redis_) return true;
        try {
            static const std::string script =
                "local ok = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2])\n"
                "if ok then return 1 else return 0 end";
            long long seconds = std::max<long long>(1, ttl.count());
            auto result = redis_->eval<long long>(script, {key}, {value, std::to_string(seconds)});
            return result == 1;
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] acquire error: " << e.what() << "\n";
            return true;
        }
    }

    bool releaseDeviceLock(const std::string& key, const std::string& value) {
        if (!redis_) return true;
        static const std::string script =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
            "return redis.call('DEL', KEYS[1]) else return 0 end";
        try {
            auto res = redis_->eval<long long>(script, {key}, {value});
            return res == 1;
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] release error: " << e.what() << "\n";
            return false;
        }
    }

    void addLiveDevice(const std::string& deviceId) {
        if (!redis_ || deviceId.empty()) return;
        try {
            redis_->sadd(LIVE_SET_KEY, deviceId);
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] sadd error: " << e.what() << "\n";
        }
    }

    void removeLiveDevice(const std::string& deviceId) {
        if (!redis_ || deviceId.empty()) return;
        try {
            redis_->srem(LIVE_SET_KEY, deviceId);
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] srem error: " << e.what() << "\n";
        }
    }

    void upsertLiveDevice(const std::string& deviceId, const std::string& connId, const json& info) {
        if (!redis_ || deviceId.empty()) return;
        try {
            setDeviceInfo(deviceId, info);
            if (!connId.empty()) {
                redis_->hset(connInfoKey(deviceId), "connId", connId);
            }
            redis_->sadd(LIVE_SET_KEY, deviceId);
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] upsert error: " << e.what() << "\n";
        }
    }

    bool removeLiveDeviceIfMatch(const std::string& deviceId, const std::string& connId) {
        if (!redis_ || deviceId.empty()) return true;
        try {
            static const std::string script =
                "local infoKey = KEYS[1]\n"
                "local liveKey = KEYS[2]\n"
                "local expected = ARGV[1]\n"
                "local device = ARGV[2]\n"
                "local current = redis.call('HGET', infoKey, 'connId')\n"
                "if (not current) or current == expected or expected == '' then\n"
                "  redis.call('DEL', infoKey)\n"
                "  redis.call('SREM', liveKey, device)\n"
                "  return 1\n"
                "end\n"
                "return 0";
            auto result = redis_->eval<long long>(script, {connInfoKey(deviceId), LIVE_SET_KEY}, {connId, deviceId});
            return result == 1;
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] remove-if-match error: " << e.what() << "\n";
            return false;
        }
    }

    long long getLiveCount() {
        if (!redis_) return 0;
        try {
            return redis_->scard(LIVE_SET_KEY);
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] scard error: " << e.what() << "\n";
            return 0;
        }
    }

    bool isDeviceLive(const std::string& deviceId) {
        if (!redis_ || deviceId.empty()) return false;
        try {
            return redis_->sismember(LIVE_SET_KEY, deviceId);
        } catch (...) {
            return false;
        }
    }

    std::vector<std::string> sampleLive(std::size_t limit) {
        std::vector<std::string> out;
        if (!redis_ || limit == 0) return out;
        try {
            long long cursor = 0;
            do {
                std::vector<std::string> chunk;
                chunk.reserve(static_cast<std::size_t>(limit * 2));
                cursor = redis_->sscan(LIVE_SET_KEY, cursor, "*", static_cast<long long>(std::max<std::size_t>(limit * 2, 10)), std::back_inserter(chunk));
                for (const auto& it : chunk) {
                    out.push_back(it);
                    if (out.size() >= limit) break;
                }
            } while (cursor != 0 && out.size() < limit);
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] sscan error: " << e.what() << "\n";
        }
        if (out.size() > limit) out.resize(limit);
        return out;
    }

    void setDeviceInfo(const std::string& deviceId, const json& info) {
        if (!redis_ || deviceId.empty()) return;
        try {
            std::vector<std::pair<std::string, std::string>> fields;
            fields.reserve(info.size());
            for (const auto& item : info.items()) {
                fields.emplace_back(item.key(), item.value().is_string() ? item.value().get<std::string>() : item.value().dump());
            }
            if (!fields.empty()) {
                redis_->hset(connInfoKey(deviceId), fields.begin(), fields.end());
            }
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] hset error: " << e.what() << "\n";
        }
    }

    void removeDeviceInfo(const std::string& deviceId) {
        if (!redis_ || deviceId.empty()) return;
        try {
            redis_->del(connInfoKey(deviceId));
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] del info error: " << e.what() << "\n";
        }
    }

    json getDeviceInfo(const std::string& deviceId) {
        json out = json::object();
        if (!redis_ || deviceId.empty()) return out;
        try {
            std::unordered_map<std::string, std::string> map;
            redis_->hgetall(connInfoKey(deviceId), std::inserter(map, map.end()));
            for (const auto& [k, v] : map) out[k] = v;
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] hgetall error: " << e.what() << "\n";
        }
        return out;
    }

    void resetRegistry() {
        if (!redis_) return;
        try {
            redis_->del(LIVE_SET_KEY);
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] reset set error: " << e.what() << "\n";
        }
        cleanupPattern(std::string(CONN_INFO_PREFIX) + "*");
        cleanupPattern("ws:dev:*");
    }

    bool publishPush(const std::string& deviceId, const json& payload) {
        if (!pub_) return false;
        try {
            json message = {
                {"deviceId", deviceId},
                {"payload", payload}
            };
            pub_->publish(PUSH_CHANNEL, message.dump());
            return true;
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] publish push error: " << e.what() << "\n";
            return false;
        }
    }

    bool publishKick(const std::string& deviceId, const std::string& reason) {
        if (!pub_) return false;
        try {
            json payload = {
                {"deviceId", deviceId},
                {"reason", reason}
            };
            pub_->publish(KICK_CHANNEL, payload.dump());
            return true;
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] publish kick error: " << e.what() << "\n";
            return false;
        }
    }

    void startPushLoop(PushHandler handler) {
        if (!sub_ || pushThread_.joinable()) return;
        try {
            pushSub_ = std::make_unique<sw::redis::Subscriber>(sub_->subscriber());
            pushSub_->subscribe(PUSH_CHANNEL);
            pushSub_->on_message([handler](const std::string& channel, const std::string& msg) {
                if (channel != PUSH_CHANNEL) return;
                try {
                    auto parsed = json::parse(msg);
                    const std::string deviceId = parsed.value<std::string>("deviceId", "");
                    if (deviceId.empty()) return;
                    handler(deviceId, parsed.contains("payload") ? parsed["payload"] : json::object());
                } catch (...) {
                }
            });
            pushThread_ = std::thread([this]() { consumeLoop(pushSub_.get()); });
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] push subscribe error: " << e.what() << "\n";
        }
    }

    void startKickLoop(KickHandler handler) {
        if (!sub_ || kickThread_.joinable()) return;
        try {
            kickSub_ = std::make_unique<sw::redis::Subscriber>(sub_->subscriber());
            kickSub_->subscribe(KICK_CHANNEL);
            kickSub_->on_message([handler](const std::string& channel, const std::string& msg) {
                if (channel != KICK_CHANNEL) return;
                try {
                    auto parsed = json::parse(msg);
                    const std::string deviceId = parsed.value<std::string>("deviceId", "");
                    if (deviceId.empty()) return;
                    handler(deviceId, parsed.value<std::string>("reason", "takeover"));
                } catch (...) {
                }
            });
            kickThread_ = std::thread([this]() { consumeLoop(kickSub_.get()); });
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] kick subscribe error: " << e.what() << "\n";
        }
    }

    void stop() {
        stop_.store(true);
        if (pushSub_) {
            try { pushSub_->unsubscribe(PUSH_CHANNEL); } catch (...) {}
        }
        if (kickSub_) {
            try { kickSub_->unsubscribe(KICK_CHANNEL); } catch (...) {}
        }
        if (pushThread_.joinable()) pushThread_.join();
        if (kickThread_.joinable()) kickThread_.join();
    }

private:
    static std::string connInfoKey(const std::string& deviceId) {
        return std::string(CONN_INFO_PREFIX) + deviceId;
    }

    void consumeLoop(sw::redis::Subscriber* subscriber) {
        if (!subscriber) return;
        while (!stop_.load()) {
            try {
                subscriber->consume();
            } catch (const sw::redis::Error& e) {
                if (stop_.load()) break;
                std::cerr << "[Redis] consume error: " << e.what() << "\n";
                std::this_thread::sleep_for(std::chrono::milliseconds(200));
            }
        }
    }

    void cleanupPattern(const std::string& pattern) {
        if (!redis_) return;
        try {
            long long cursor = 0;
            do {
                std::vector<std::string> keys;
                cursor = redis_->scan(cursor, pattern, 1000, std::back_inserter(keys));
                if (!keys.empty()) {
                    redis_->del(keys.begin(), keys.end());
                }
            } while (cursor != 0);
        } catch (const sw::redis::Error& e) {
            std::cerr << "[Redis] cleanup error: " << e.what() << "\n";
        }
    }

    static constexpr const char* LIVE_SET_KEY = "ws:live";
    static constexpr const char* CONN_INFO_PREFIX = "ws:conn:";
    static constexpr const char* PUSH_CHANNEL = "ws:push";
    static constexpr const char* KICK_CHANNEL = "ws:kick";

    std::unique_ptr<sw::redis::Redis> redis_;
    std::unique_ptr<sw::redis::Redis> pub_;
    std::unique_ptr<sw::redis::Redis> sub_;
    std::unique_ptr<sw::redis::Subscriber> pushSub_;
    std::unique_ptr<sw::redis::Subscriber> kickSub_;
    std::thread pushThread_;
    std::thread kickThread_;
    std::atomic<bool> stop_{false};
};

struct PerSocketData {
    std::string userId;
    std::string appId;
    std::string deviceId;
    std::string connId;
    std::string ip;
    std::string deviceKey;
    std::string lockValue;
    std::uint64_t exp{0};
    std::uint64_t openedTs{0};
    bool alive{false};
    bool registered{false};
    std::uint64_t ignoreKickUntil{0};
    std::deque<std::string> outbox;
    std::shared_ptr<std::atomic_bool> expiryGuard;
};

template <bool SSL>
struct AppSelector;

template <>
struct AppSelector<false> { using AppType = uWS::App; using WsType = uWS::WebSocket<false, true, PerSocketData>; };

template <>
struct AppSelector<true> { using AppType = uWS::SSLApp; using WsType = uWS::WebSocket<true, true, PerSocketData>; };

template <bool SSL>
struct SharedState {
    using WsType = typename AppSelector<SSL>::WsType;

    ServerConfig cfg;
    std::shared_ptr<RabbitPublisher> rabbit;
    std::shared_ptr<RedisManager> redis;
    uWS::Loop* loop{nullptr};
    uWS::CompressOptions compression{uWS::DISABLED};

    std::mutex mutex;
    std::unordered_map<std::string, WsType*> byDevice;
    std::unordered_map<std::string, WsType*> byConn;

    WsType* getByDevice(const std::string& deviceId) {
        std::scoped_lock lock(mutex);
        auto it = byDevice.find(deviceId);
        return it != byDevice.end() ? it->second : nullptr;
    }

    WsType* getByConn(const std::string& connId) {
        std::scoped_lock lock(mutex);
        auto it = byConn.find(connId);
        return it != byConn.end() ? it->second : nullptr;
    }

    void trackConn(WsType* ws) {
        auto* data = ws->getUserData();
        if (data->connId.empty()) return;
        std::scoped_lock lock(mutex);
        byConn[data->connId] = ws;
    }

    void untrackConn(const std::string& connId) {
        if (connId.empty()) return;
        std::scoped_lock lock(mutex);
        byConn.erase(connId);
    }

    void registerDevice(WsType* ws) {
        auto* data = ws->getUserData();
        if (data->registered) return;
        data->registered = true;
        if (!data->deviceId.empty()) {
            std::scoped_lock lock(mutex);
            byDevice[data->deviceId] = ws;
        }
    }

    void unregisterDevice(const std::string& deviceId, WsType* ws) {
        if (deviceId.empty()) return;
        std::scoped_lock lock(mutex);
        auto it = byDevice.find(deviceId);
        if (it != byDevice.end() && (!ws || it->second == ws)) {
            byDevice.erase(it);
        }
    }
};

template <typename Res>
void respondJson(Res* res, int code, const json& payload, const std::vector<std::pair<std::string, std::string>>& headers = {}) {
    std::string body = payload.dump();
    std::string status = statusString(code);
    res->cork([res, status = std::move(status), body = std::move(body), headers]() mutable {
        res->writeStatus(status.c_str());
        res->writeHeader("content-type", "application/json");
        for (const auto& h : headers) {
            if (!h.first.empty() && !h.second.empty()) {
                res->writeHeader(h.first.c_str(), h.second.c_str());
            }
        }
        res->end(body);
    });
}

template <typename Res>
void respondText(Res* res, int code, std::string body, std::string contentType = "text/plain") {
    std::string status = statusString(code);
    res->cork([res, status = std::move(status), body = std::move(body), contentType = std::move(contentType)]() mutable {
        res->writeStatus(status.c_str());
        res->writeHeader("content-type", contentType.c_str());
        res->end(body);
    });
}

template <typename Res>
void readJsonBody(Res* res, std::function<void(std::optional<json>, std::optional<std::string>)> callback) {
    auto body = std::make_shared<std::string>();
    auto done = std::make_shared<std::atomic_bool>(false);
    res->onAborted([callback, done]() mutable {
        if (done->exchange(true)) return;
        callback(std::nullopt, std::make_optional<std::string>("aborted"));
    });
    res->onData([body, callback, done](std::string_view data, bool last) mutable {
        if (done->load()) return;
        body->append(data.data(), data.size());
        if (last) {
            if (done->exchange(true)) return;
            try {
                callback(json::parse(*body), std::nullopt);
            } catch (const std::exception& e) {
                callback(std::nullopt, std::make_optional<std::string>(e.what()));
            }
        }
    });
}

std::optional<json> safeJson(std::string_view data) {
    try {
        return json::parse(data);
    } catch (...) {
        return std::nullopt;
    }
}

template <bool SSL>
std::string getClientIp(uWS::HttpResponse<SSL>* res, uWS::HttpRequest& req) {
    std::string_view xff = req.getHeader("x-forwarded-for");
    if (!xff.empty()) {
        auto comma = xff.find(',');
        std::string candidate = std::string(xff.substr(0, comma == std::string_view::npos ? xff.size() : comma));
        return normalizeIp(candidate);
    }
    if (res) {
        std::string_view raw = res->getRemoteAddressAsText();
        if (!raw.empty()) return normalizeIp(std::string(raw));
    }
    return {};
}

std::optional<JwtPayload> verifyJwt(const std::string& token, const ServerConfig& cfg, std::string& error) {
    try {
        auto decoded = jwt::decode(token);
        auto verifier = jwt::verify()
            .allow_algorithm(jwt::algorithm::rs256(cfg.authPublicPem, "", "", ""))
            .leeway(static_cast<std::size_t>(cfg.jwtLeeway.count()));
        verifier.verify(decoded);

        JwtPayload payload;
        auto tryString = [&](std::string_view claim) -> std::optional<std::string> {
            std::string name{claim};
            if (!decoded.has_payload_claim(name)) return std::nullopt;
            try {
                return decoded.get_payload_claim(name).as_string();
            } catch (...) {
                return std::nullopt;
            }
        };

        payload.deviceId = tryString("deviceId").value_or(tryString("did").value_or(""));
        payload.appId = tryString("appId").value_or(tryString("aid").value_or(tryString("sub").value_or("")));
        payload.userId = tryString("sub").value_or(tryString("userId").value_or(""));
        payload.ip = tryString("ip").value_or("");
        if (decoded.has_expires_at()) {
            auto expPoint = decoded.get_expires_at();
            payload.exp = static_cast<std::uint64_t>(
                std::chrono::duration_cast<std::chrono::seconds>(expPoint.time_since_epoch()).count());
        }
        return payload;
    } catch (const std::exception& e) {
        error = e.what();
        return std::nullopt;
    }
}

std::string getQueryParamCI(std::string_view query, std::string_view name) {
    std::string needle = toLower(std::string(name));
    std::size_t start = 0;
    while (start < query.size()) {
        auto end = query.find('&', start);
        if (end == std::string_view::npos) end = query.size();
        auto part = query.substr(start, end - start);
        auto eq = part.find('=');
        std::string key = eq == std::string_view::npos ? std::string(part) : std::string(part.substr(0, eq));
        if (toLower(trim(key)) == needle) {
            if (eq == std::string_view::npos) return {};
            std::string value(part.substr(eq + 1));
            return trim(value);
        }
        start = end + 1;
    }
    return {};
}

ServerConfig loadConfig() {
    ServerConfig cfg;
    cfg.port = envPort("PORT", 8080);
    cfg.authPublicPem = loadPublicPem();
    cfg.rabbitUrl = trim(getEnv("RABBIT_URL"));
    cfg.rabbitExchange = trim(getEnv("RABBIT_EXCHANGE", cfg.rabbitExchange));
    cfg.rabbitRoutingKey = trim(getEnv("RABBIT_ROUTING_KEY", cfg.rabbitRoutingKey));
    cfg.redisUrl = trim(getEnv("REDIS_URL"));
    cfg.idleTimeout = static_cast<unsigned>(envUInt64("IDLE_TIMEOUT", cfg.idleTimeout));
    cfg.maxPayload = envUInt64("MAX_PAYLOAD", cfg.maxPayload);
    cfg.maxBackpressure = envUInt64("MAX_BACKPRESSURE", cfg.maxBackpressure);
    cfg.authDevMode = envFlag("AUTH_DEV_MODE", cfg.authDevMode);
    cfg.wsWelcome = envFlag("WS_WELCOME", cfg.wsWelcome);
    cfg.wsAck = envFlag("WS_ACK", cfg.wsAck);
    cfg.debugWs = envFlag("DEBUG_WS", cfg.debugWs);
    cfg.compressionShared = toLower(trim(getEnv("WS_COMPRESSION", "none"))) == "shared";
    cfg.useSsl = !trim(getEnv("SSL_KEY")).empty() && !trim(getEnv("SSL_CERT")).empty();
    cfg.sslKeyFile = trim(getEnv("SSL_KEY"));
    cfg.sslCertFile = trim(getEnv("SSL_CERT"));
    return cfg;
}

template <bool SSL>
void enqueueMessage(typename SharedState<SSL>::WsType* ws, PerSocketData* data, std::string payload, uWS::OpCode opCode = uWS::OpCode::TEXT) {
    data->outbox.emplace_back(std::move(payload));
    while (!data->outbox.empty()) {
        const std::string& front = data->outbox.front();
        if (!ws->send(front, opCode)) {
            break;
        }
        data->outbox.pop_front();
    }
}

template <bool SSL>
void flushQueue(typename SharedState<SSL>::WsType* ws, PerSocketData* data) {
    while (!data->outbox.empty()) {
        const std::string& front = data->outbox.front();
        if (!ws->send(front, uWS::OpCode::TEXT)) {
            break;
        }
        data->outbox.pop_front();
    }
}

template <bool SSL>
int runServer(const ServerConfig& cfg) {
    using AppType = typename AppSelector<SSL>::AppType;
    using WsType = typename AppSelector<SSL>::WsType;

    auto state = std::make_shared<SharedState<SSL>>();
    state->cfg = cfg;
    state->compression = cfg.compressionShared ? uWS::SHARED_COMPRESSOR : uWS::DISABLED;
    state->rabbit = std::make_shared<RabbitPublisher>(cfg.rabbitUrl, cfg.rabbitExchange);
    if (!cfg.redisUrl.empty()) {
        state->redis = std::make_shared<RedisManager>(cfg.redisUrl);
        if (state->redis && state->redis->enabled()) {
            state->redis->resetRegistry();
        } else {
            state->redis.reset();
        }
    }

    std::unique_ptr<AppType> appPtr;
    if constexpr (SSL) {
        uWS::SocketContextOptions opts;
        opts.key_file_name = cfg.sslKeyFile.c_str();
        opts.cert_file_name = cfg.sslCertFile.c_str();
        appPtr = std::make_unique<AppType>(opts);
    } else {
        appPtr = std::make_unique<AppType>();
    }
    auto& app = *appPtr;

    state->loop = uWS::Loop::get();

    std::weak_ptr<SharedState<SSL>> weakState = state;

    if (state->redis) {
        auto redis = state->redis;
        redis->startPushLoop([weakState](const std::string& deviceId, const json& payload) {
            if (auto st = weakState.lock()) {
                st->loop->defer([weakState, deviceId, payload]() {
                    auto locked = weakState.lock();
                    if (!locked) return;
                    auto ws = locked->getByDevice(deviceId);
                    if (!ws) return;
                    auto* data = ws->getUserData();
                    if (!data->alive) return;
                    enqueueMessage<SSL>(ws, data, payload.dump());
                });
            }
        });
        redis->startKickLoop([weakState](const std::string& deviceId, const std::string& reason) {
            if (auto st = weakState.lock()) {
                st->loop->defer([weakState, deviceId, reason]() {
                    auto locked = weakState.lock();
                    if (!locked) return;
                    auto ws = locked->getByDevice(deviceId);
                    if (!ws) return;
                    auto* data = ws->getUserData();
                    if (!data->alive) return;
                    auto now = nowMs();
                    if (data->ignoreKickUntil && now < data->ignoreKickUntil) return;
                    ws->end(4401, reason.empty() ? "takeover" : reason);
                });
            }
        });
    }

    auto registerConnection = [weakState](WsType* ws) {
        if (auto st = weakState.lock()) {
            auto* data = ws->getUserData();
            st->registerDevice(ws);
            if (st->redis && st->redis->enabled() && !data->deviceId.empty()) {
                auto redis = st->redis;
                json info = {
                    {"appId", data->appId},
                    {"connId", data->connId},
                    {"ip", data->ip},
                    {"ts", data->openedTs},
                    {"exp", data->exp}
                };
                std::string deviceId = data->deviceId;
                std::string connId = data->connId;
                std::thread([redis, deviceId, info, weakState, connId]() {
                    if (!redis) return;
                    auto stillAlive = [&]() -> bool {
                        if (auto guard = weakState.lock()) {
                            auto wsCurrent = guard->getByConn(connId);
                            if (!wsCurrent) return false;
                            auto* live = wsCurrent->getUserData();
                            return live && live->alive;
                        }
                        return false;
                    };
                    if (!stillAlive()) return;
                    redis->upsertLiveDevice(deviceId, connId, info);
                }).detach();
            }
        }
    };

    auto cleanupConnection = [weakState](PerSocketData* data, WsType* ws) {
        if (auto st = weakState.lock()) {
            st->unregisterDevice(data->deviceId, ws);
            st->untrackConn(data->connId);
            if (st->redis && st->redis->enabled() && !data->deviceId.empty()) {
                auto redis = st->redis;
                std::string deviceId = data->deviceId;
                std::string connId = data->connId;
                std::thread([redis, deviceId, connId]() {
                    if (!redis) return;
                    if (!redis->removeLiveDeviceIfMatch(deviceId, connId)) {
                        redis->removeLiveDevice(deviceId);
                        redis->removeDeviceInfo(deviceId);
                    }
                }).detach();
            }
        }
    };

    app.get("/health", [](auto* res, auto*) {
        respondText(res, 200, "ok");
    });

    app.ws<PerSocketData>("/*", {
        .compression = state->compression,
        .maxPayloadLength = cfg.maxPayload,
        .idleTimeout = static_cast<unsigned short>(
            std::min<unsigned>(cfg.idleTimeout, std::numeric_limits<unsigned short>::max())),
        .maxBackpressure = cfg.maxBackpressure,
        .upgrade = [cfg, weakState](auto* res, auto* req, auto* context) {
            std::string_view protocol = req->getHeader("sec-websocket-protocol");
            std::string_view authz = req->getHeader("authorization");
            std::string token;
            if (!protocol.empty()) token = trim(std::string(protocol));
            if (token.empty() && !authz.empty()) {
                std::string value = trim(std::string(authz));
                const std::string bearer = "bearer ";
                std::string lower = toLower(value);
                if (lower.rfind(bearer, 0) == 0) {
                    token = trim(value.substr(bearer.size()));
                } else {
                    token = std::move(value);
                }
            }
            if (token.empty()) {
                std::string queryParam = getQueryParamCI(req->getQuery(), "token");
                if (!queryParam.empty()) token = queryParam;
            }
            if (token.empty()) {
                res->writeStatus("401 Unauthorized");
                res->end();
                return;
            }

            JwtPayload payload;
            if (!cfg.authDevMode) {
                std::string error;
                auto verified = verifyJwt(token, cfg, error);
                if (!verified) {
                    if (cfg.debugWs) std::cerr << "[UPGRADE] jwt error: " << error << "\n";
                    res->writeStatus("401 Unauthorized");
                    res->end();
                    return;
                }
                payload = *verified;
            } else {
                payload.deviceId = "dev";
                payload.appId = "dev";
                payload.userId = "dev";
                payload.exp = static_cast<std::uint64_t>(std::chrono::duration_cast<std::chrono::seconds>(
                    std::chrono::system_clock::now().time_since_epoch()).count()) + 3600;
            }

            if (payload.deviceId.empty()) {
                res->writeStatus("401 Unauthorized");
                res->end();
                return;
            }

            std::string clientIp = getClientIp<SSL>(res, *req);
            if (!payload.ip.empty() && !clientIp.empty()) {
                std::string normTokenIp = normalizeIp(payload.ip);
                std::string normClientIp = normalizeIp(clientIp);
                if (normTokenIp != normClientIp) {
                    if (cfg.debugWs) std::cerr << "[UPGRADE] ip mismatch token=" << payload.ip << " client=" << clientIp << "\n";
                    res->writeStatus("401 Unauthorized");
                    res->end();
                    return;
                }
            }

            std::string secKey = std::string(req->getHeader("sec-websocket-key"));
            std::string secProtocol = std::string(protocol);
            std::string secExtensions = std::string(req->getHeader("sec-websocket-extensions"));

            PerSocketData data;
            data.userId = payload.userId.empty() ? "unknown" : payload.userId;
            data.appId = payload.appId.empty() ? "unknown" : payload.appId;
            data.deviceId = payload.deviceId;
            data.connId = randomHex();
            std::string chosenIp = payload.ip.empty() ? clientIp : payload.ip;
            data.ip = normalizeIp(chosenIp);
            data.deviceKey = buildDeviceKey(data.appId, data.deviceId);
            data.exp = payload.exp;

            res->upgrade(
                std::move(data),
                secKey,
                secProtocol,
                secExtensions,
                context);
        },
        .open = [cfg, weakState, registerConnection](WsType* ws) {
            auto* data = ws->getUserData();
            data->alive = true;
            data->openedTs = nowMs();
            data->outbox.clear();
            data->expiryGuard = std::make_shared<std::atomic_bool>(true);

            if (auto st = weakState.lock()) {
                st->trackConn(ws);
            }

            if (cfg.wsWelcome) {
                json welcome = {
                    {"type", "welcome"},
                    {"userId", data->userId},
                    {"deviceId", data->deviceId},
                    {"connId", data->connId}
                };
                enqueueMessage<SSL>(ws, data, welcome.dump());
            }

            auto state = weakState.lock();
            if (!state) return;
            if (!state->redis || !state->redis->enabled()) {
                registerConnection(ws);
                return;
            }

            auto redis = state->redis;
            std::string connId = data->connId;
            std::string deviceKey = data->deviceKey;
            std::string deviceId = data->deviceId;
            std::uint64_t exp = data->exp;
            std::weak_ptr<SharedState<SSL>> wsWeak = weakState;

            std::thread([redis, wsWeak, registerConnection, connId, deviceKey, deviceId, exp]() {
                const auto nowSeconds = static_cast<std::uint64_t>(std::chrono::duration_cast<std::chrono::seconds>(
                    std::chrono::system_clock::now().time_since_epoch()).count());
                std::uint64_t ttl = exp > nowSeconds ? (exp - nowSeconds) : 60;
                if (ttl == 0) ttl = 60;
                std::string lockValue = randomHex();
                bool acquired = false;
                for (int attempt = 0; attempt < 3 && !acquired; ++attempt) {
                    acquired = redis->acquireDeviceLock(deviceKey, lockValue, std::chrono::seconds(ttl));
                    if (acquired) break;
                    if (auto st = wsWeak.lock()) {
                        st->loop->defer([wsWeak, deviceId]() {
                            if (auto inner = wsWeak.lock()) {
                                auto wsOld = inner->getByDevice(deviceId);
                                if (wsOld) {
                                    auto* oldData = wsOld->getUserData();
                                    oldData->ignoreKickUntil = nowMs() + 500;
                                    wsOld->end(4401, "takeover");
                                }
                            }
                        });
                    }
                    redis->publishKick(deviceId, "takeover");
                    std::this_thread::sleep_for(std::chrono::milliseconds(150));
                }

                if (auto st = wsWeak.lock()) {
                    st->loop->defer([wsWeak, connId, lockValue, acquired, registerConnection]() {
                        auto holding = wsWeak.lock();
                        if (!holding) return;
                        auto wsPtr = holding->getByConn(connId);
                        if (!wsPtr) return;
                        auto* wsData = wsPtr->getUserData();
                        if (!wsData->alive) return;
                        if (!acquired) {
                            json err = {{"type", "error"}, {"error", "duplicate_connection"}};
                            enqueueMessage<SSL>(wsPtr, wsData, err.dump());
                            wsPtr->end(1013, "dup");
                            return;
                        }
                        wsData->lockValue = lockValue;
                        registerConnection(wsPtr);
                        wsData->ignoreKickUntil = nowMs() + 500;
                    });
                }
            }).detach();

            if (data->exp > 0) {
                auto guard = data->expiryGuard;
                std::string connId = data->connId;
                auto expiryMs = data->exp * 1000;
                std::thread([wsWeak, guard, connId, expiryMs]() {
                    std::uint64_t now = nowMs();
                    std::uint64_t delay = expiryMs > now ? expiryMs - now + 1500 : 1500;
                    std::this_thread::sleep_for(std::chrono::milliseconds(delay));
                    if (!guard || !guard->load()) return;
                    if (auto st = wsWeak.lock()) {
                        st->loop->defer([wsWeak, connId]() {
                            auto locked = wsWeak.lock();
                            if (!locked) return;
                            auto ws = locked->getByConn(connId);
                            if (!ws) return;
                            ws->end(1000, "exp");
                        });
                    }
                }).detach();
            }
        },
        .message = [cfg, weakState](WsType* ws, std::string_view msg, uWS::OpCode opCode) {
            auto* data = ws->getUserData();
            if (!data->alive) return;
            auto parsed = safeJson(msg);
            if (!parsed) {
                json err = {{"type", "error"}, {"error", "bad_json"}};
                enqueueMessage<SSL>(ws, data, err.dump());
                return;
            }

            json& obj = *parsed;
            std::string topic = cfg.rabbitRoutingKey;
            if (obj.contains("topic") && obj["topic"].is_string()) {
                topic = obj["topic"].get<std::string>();
                if (topic.empty()) topic = cfg.rabbitRoutingKey;
            }

            json payload;
            if (obj.contains("data")) {
                payload = obj["data"];
            } else {
                payload = obj;
            }

            json envelope = {
                {"topic", topic},
                {"data", payload},
                {"sender", {
                    {"kind", "android"},
                    {"appId", data->appId},
                    {"deviceId", data->deviceId},
                    {"ip", data->ip},
                    {"connId", data->connId}
                }},
                {"ts", nowMs()}
            };

            bool published = true;
            if (auto st = weakState.lock()) {
                published = st->rabbit->publish(topic, envelope);
            }

            if (obj.contains("type") && obj["type"].is_string() && obj["type"].get<std::string>() == "ping") {
                json pong = {{"type", "pong"}, {"t", nowMs()}};
                enqueueMessage<SSL>(ws, data, pong.dump());
                return;
            }

            if (cfg.wsAck) {
                json ack;
                ack["id"] = obj.contains("id") ? obj["id"] : json(nullptr);
                if (published) {
                    ack["type"] = "ack";
                } else {
                    ack = {
                        {"type", "error"},
                        {"error", "publish_failed"},
                        {"id", ack["id"]}
                    };
                }
                enqueueMessage<SSL>(ws, data, ack.dump());
            }
        },
        .drain = [](WsType* ws) {
            auto* data = ws->getUserData();
            flushQueue<SSL>(ws, data);
        },
        .close = [cfg, weakState, cleanupConnection](WsType* ws, int, std::string_view) {
            auto* data = ws->getUserData();
            data->alive = false;
            if (data->expiryGuard) data->expiryGuard->store(false);
            data->outbox.clear();
            cleanupConnection(data, ws);

            if (!data->deviceKey.empty() && !data->lockValue.empty()) {
                if (auto st = weakState.lock()) {
                    if (st->redis && st->redis->enabled()) {
                        auto redis = st->redis;
                        std::string key = data->deviceKey;
                        std::string value = data->lockValue;
                        std::thread([redis, key, value]() {
                            redis->releaseDeviceLock(key, value);
                        }).detach();
                    }
                }
            }
        }
    });

    app.get("/debug/redis", [weakState](auto* res, auto* req) {
        json out;
    std::string requestIp = getClientIp<SSL>(res, *req);
        auto st = weakState.lock();
        if (!st || !st->redis || !st->redis->enabled()) {
            out = {
                {"enabled", false},
                {"requestIp", requestIp}
            };
            respondJson(res, 200, out);
            return;
        }
        try {
            long long count = st->redis->getLiveCount();
            auto ids = st->redis->sampleLive(20);
            json sample = json::array();
            for (const auto& id : ids) {
                auto info = st->redis->getDeviceInfo(id);
                info["deviceId"] = id;
                info["online"] = true;
                sample.push_back(info);
            }
            out = {
                {"enabled", true},
                {"count", count},
                {"sample", sample},
                {"requestIp", requestIp}
            };
            respondJson(res, 200, out);
        } catch (const std::exception& e) {
            respondJson(res, 500, {{"error", e.what()}, {"requestIp", requestIp}});
        }
    });

    app.get("/debug/conn/:id", [weakState](auto* res, auto* req) {
        std::string id = std::string(req->getParameter(0));
    std::string requestIp = getClientIp<SSL>(res, *req);
        auto st = weakState.lock();
        if (!st || !st->redis || !st->redis->enabled()) {
            respondJson(res, 200, {
                {"enabled", false},
                {"requestIp", requestIp}
            });
            return;
        }
        json info = st->redis->getDeviceInfo(id);
        bool online = st->redis->isDeviceLive(id);
        respondJson(res, 200, {
            {"id", id},
            {"online", online},
            {"info", info},
            {"requestIp", requestIp}
        });
    });

    app.get("/connections", [weakState](auto* res, auto* req) {
        // Determine allowed origin: prefer request Origin, else ALLOW_ORIGIN env or default to localhost:5173
        std::string origin = std::string(req->getHeader("origin"));
        std::string allowOrigin = origin.empty() ? getEnv("ALLOW_ORIGIN", "http://localhost:5173") : origin;
        std::vector<std::pair<std::string, std::string>> corsHeaders = {{"Access-Control-Allow-Origin", allowOrigin}};

        auto st = weakState.lock();
        if (!st) {
            respondJson(res, 500, {{"error", "server_stopped"}}, corsHeaders);
            return;
        }
        json localSample = json::array();
        {
            std::scoped_lock lock(st->mutex);
            std::size_t count = 0;
            for (const auto& [deviceId, ws] : st->byDevice) {
                if (!ws) continue;
                auto* data = ws->getUserData();
                std::uint64_t openedMs = data->openedTs;
                std::uint64_t expiryMs = data->exp ? data->exp * 1000ull : 0ull;
                if (!expiryMs && openedMs) {
                    expiryMs = openedMs + 24ull * 60ull * 60ull * 1000ull;
                }
                localSample.push_back({
                    {"deviceId", deviceId},
                    {"appId", data->appId},
                    {"connId", data->connId},
                    {"ip", data->ip},
                    {"ts", openedMs},
                    {"startMs", openedMs},
                    {"exp", data->exp},
                    {"expiryMs", expiryMs},
                    {"online", data->alive}
                });
                if (++count >= 10000) break;
            }
        }

        if (st->redis && st->redis->enabled()) {
            long long total = st->redis->getLiveCount();
            auto ids = st->redis->sampleLive(50);
            json sample = json::array();
            for (const auto& id : ids) {
                auto info = st->redis->getDeviceInfo(id);
                auto extractString = [&](const char* key) -> std::string {
                    if (info.contains(key)) {
                        const auto& val = info[key];
                        if (val.is_string()) return val.get<std::string>();
                        if (val.is_number_integer()) return std::to_string(val.get<long long>());
                        if (val.is_number_unsigned()) return std::to_string(val.get<unsigned long long>());
                        if (val.is_number_float()) return std::to_string(val.get<double>());
                    }
                    return {};
                };
                std::string tsStr = extractString("ts");
                long long tsValue = 0;
                if (!tsStr.empty()) {
                    try { tsValue = std::stoll(tsStr); } catch (...) { tsValue = 0; }
                }
                std::string expStr = extractString("exp");
                long long expSeconds = 0;
                if (!expStr.empty()) {
                    try { expSeconds = std::stoll(expStr); } catch (...) { expSeconds = 0; }
                }
                long long expiryMs = 0;
                if (expSeconds > 0) {
                    expiryMs = expSeconds * 1000ll;
                } else if (tsValue > 0) {
                    expiryMs = tsValue + 24ll * 60ll * 60ll * 1000ll;
                }
                sample.push_back({
                    {"deviceId", id},
                    {"appId", extractString("appId")},
                    {"connId", extractString("connId")},
                    {"ip", extractString("ip")},
                    {"ts", tsValue},
                    {"startMs", tsValue},
                    {"exp", expSeconds},
                    {"expiryMs", expiryMs},
                    {"online", true}
                });
            }
            respondJson(res, 200, {{"count", total}, {"sample", sample}}, corsHeaders);
            return;
        }

        respondJson(res, 200, {{"count", localSample.size()}, {"sample", localSample}}, corsHeaders);
    });

    app.post("/push/device", [weakState](auto* res, auto* req) {
    std::string requestIp = getClientIp<SSL>(res, *req);
        readJsonBody(res, [weakState, res, requestIp](std::optional<json> body, std::optional<std::string> err) {
            if (err) {
                respondJson(res, 400, {{"error", "bad_json"}});
                return;
            }
            if (!body || !body->contains("deviceId") || !body->contains("payload")) {
                respondJson(res, 400, {{"error", "missing_fields"}});
                return;
            }
            std::string deviceId = (*body)["deviceId"].get<std::string>();
            json payload = (*body)["payload"];
            auto st = weakState.lock();
            if (!st) {
                respondJson(res, 500, {{"error", "server_stopped"}});
                return;
            }
            if (st->redis && st->redis->enabled()) {
                st->redis->publishPush(deviceId, payload);
                bool live = st->redis->isDeviceLive(deviceId);
                respondJson(res, 200, {
                    {"accepted", true},
                    {"online", live},
                    {"requestIp", requestIp}
                });
                return;
            }
            auto ws = st->getByDevice(deviceId);
            if (!ws || !ws->getUserData()->alive) {
                respondJson(res, 404, {{"error", "not_connected"}});
                return;
            }
            enqueueMessage<SSL>(ws, ws->getUserData(), payload.dump());
            respondJson(res, 200, {
                {"delivered", true},
                {"requestIp", requestIp}
            });
        });
    });

    /*app.get("/ui/connections", [](auto* res, auto*) {
        static const std::string html =
            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\" />"
            "<title>Connections</title><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />"
            "<style>body{font-family:Segoe UI,Roboto,Arial,sans-serif;background:#0f111a;color:#e0e0e0;margin:0;padding:24px}" 
            "h2{color:#00ffc8;text-shadow:0 0 8px #00ffc8;margin:0 0 16px}" 
            ".bar{display:flex;gap:8px;align-items:center;margin-bottom:12px}" 
            "button{background:#25273a;color:#e0e0e0;border:none;border-radius:8px;padding:8px 12px;font-weight:600;cursor:pointer}" 
            "button:hover{background:#00ffc8;color:#0f111a}" 
            "table{width:100%;border-collapse:collapse;background:#1b1d2a;border-radius:10px;overflow:hidden}" 
            "th,td{padding:8px 10px;border-bottom:1px solid #2e324d;font-size:13px}" 
            "th{background:#141625;text-align:left;color:#a9b2c3}" 
            "tr:hover{background:#202338}" 
            ".muted{color:#a9b2c3}</style></head><body>"
            "<h2>Connected Devices</h2><div class=\"bar\"><button onclick=\"loadNow()\">Refresh</button><span class=\"muted\" id=\"meta\"></span></div>"
            "<table><thead><tr><th>Device ID</th><th>App ID</th><th>Conn ID</th><th>IP</th><th>Start</th><th>Expiry</th><th>Online</th></tr></thead>"
            "<tbody id=\"rows\"><tr><td class=\"muted\" colspan=\"7\">Loading...</td></tr></tbody></table>"
            "<script>const meta=document.getElementById('meta');const rows=document.getElementById('rows');function normMs(v){if(!v)return 0;const n=Number(v);if(!Number.isFinite(n))return 0;if(n>1e12)return n;return n*1000;}function fmt(ts){const ms=normMs(ts);if(!ms)return '';const d=new Date(ms);if(Number.isNaN(d.getTime()))return '';return d.toLocaleString();}async function loadNow(){const res=await fetch('/connections');const j=await res.json().catch(()=>({}));rows.innerHTML='';meta.textContent='count='+(j.count||0)+' updated='+new Date().toLocaleTimeString();const sample=j.sample||[];if(!sample.length){rows.innerHTML='<tr><td class=\"muted\" colspan=\"7\">No connections</td></tr>';return;}for(const it of sample){const tr=document.createElement('tr');const start=it.startMs??it.ts??0;const expCandidate=it.expiryMs||it.exp||0;const exp=expCandidate||(start?normMs(start)+86400000:0);tr.innerHTML='<td>'+ (it.deviceId||'') +'</td>'+'<td>'+ (it.appId||'') +'</td>'+'<td>'+ (it.connId||'') +'</td>'+'<td>'+ (it.ip||'') +'</td>'+'<td>'+ fmt(start) +'</td>'+'<td>'+ fmt(exp) +'</td>'+'<td>'+ (it.online?'yes':'no') +'</td>';rows.appendChild(tr);}}loadNow();setInterval(loadNow,3000);</script></body></html>";
        respondText(res, 200, html, "text/html; charset=utf-8");
    });*/

    app.get("/ui/connections", [](auto* res, auto*) {
        static const std::string html =
            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"/>"
            "<title>Connections</title><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
            "<style>:root{--bg:#0b0f14;--fg:#e6f1ff;--muted:#9fb0c3;--line:#223049;--line2:#1e2a3f;--neon:#00ffc8;--green:#00e08f}"
            "*{box-sizing:border-box}html,body{height:100%}"
            "body{margin:0;padding:8px;color:var(--fg);background:radial-gradient(1200px 800px at 20% -10%,#0f1a22 0%,#0b0f14 50%,#070b10 100%);"
            "font-family:ui-monospace,Menlo,Consolas,monospace;letter-spacing:.2px}"
            ".wrap{width:100%;max-width:100%;margin:0}"
            "body:before,body:after{content:\"\";position:fixed;inset:0;pointer-events:none}"
            "body:before{background:radial-gradient(800px 400px at 10% 0%,rgba(0,255,200,.08) 0%,transparent 60%),radial-gradient(1200px 600px at 90% 100%,rgba(0,255,200,.05) 0%,transparent 70%);mix-blend-mode:screen;opacity:.7}"
            "body:after{background:repeating-linear-gradient(0deg,rgba(255,255,255,.02) 0 2px,transparent 2px 4px);animation:scan 8s linear infinite;opacity:.22}"
            "@keyframes scan{0%{transform:translateY(-10px)}100%{transform:translateY(10px)}}"
            "h2{margin:0 0 8px;font-weight:800;letter-spacing:.8px;color:var(--neon);text-shadow:0 0 12px var(--neon)}"
            ".bar{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:8px}"
            ".btn{background:linear-gradient(180deg,#182233,#121a2a);color:var(--fg);border:1px solid #263145;border-radius:10px;padding:8px 12px;font-weight:700;"
            "box-shadow:inset 0 0 0 1px rgba(255,255,255,.03);cursor:pointer;transition:transform .08s,box-shadow .2s,border-color .2s}"
            ".btn:hover{border-color:var(--neon);box-shadow:0 0 16px rgba(0,255,200,.2),inset 0 0 0 1px rgba(0,255,200,.15);color:#0b1518}"
            ".btn:active{transform:translateY(1px)}"
            ".toggle{display:inline-flex;align-items:center;gap:8px;padding:6px 8px;border-radius:999px;background:linear-gradient(180deg,#121a22,#0e1822);border:1px solid #233044}"
            ".toggle input{display:none}"
            ".knob{width:44px;height:24px;border-radius:999px;background:#0b1320;border:1px solid #2a3950;position:relative;transition:all .2s;box-shadow:inset 0 0 0 1px rgba(255,255,255,.03)}"
            ".knob:after{content:\"\";position:absolute;top:3px;left:3px;width:18px;height:18px;border-radius:50%;background:linear-gradient(180deg,#182233,#0c1320);box-shadow:0 1px 2px rgba(0,0,0,.6);transition:all .2s}"
            ".toggle input:checked+.knob{border-color:var(--green);background:linear-gradient(180deg,rgba(0,224,143,.18),rgba(0,224,143,.08));box-shadow:0 0 10px rgba(0,224,143,.35) inset,0 0 14px rgba(0,224,143,.25)}"
            ".toggle input:checked+.knob:after{left:23px;background:radial-gradient(circle at 60% 40%,#aaffea 0%,#00b873 60%);box-shadow:0 0 8px rgba(0,224,143,.6),0 0 12px rgba(0,224,143,.4) inset}"
            ".toggle .lbl{color:var(--muted);font-weight:700}.toggle input:checked~.lbl{color:var(--neon)}"
            ".input{display:flex;gap:6px;align-items:center;background:linear-gradient(180deg,#121a22,#0e1822);border:1px solid #233044;border-radius:10px;padding:6px 8px}"
            ".input input,.input select{appearance:none;background:transparent;border:none;color:var(--fg);font-size:13px;outline:none}"
            ".input input{width:90px;text-align:center}"
            ".meta{color:var(--muted);font-size:12px;display:flex;align-items:center;gap:6px}.meta .dot{width:6px;height:6px;border-radius:50%;background:rgba(255,255,255,.3)}"
            ".table{width:100%;overflow:auto;border-radius:12px;border:1px solid var(--line);box-shadow:0 0 24px rgba(0,255,200,.07);background:linear-gradient(180deg,rgba(17,23,38,.9),rgba(10,14,22,.9));-webkit-overflow-scrolling:touch;margin:8px 0}"
            "table{width:100%;border-collapse:separate;border-spacing:0;table-layout:fixed}"
            "thead th{position:sticky;top:0;z-index:2;background:linear-gradient(180deg,#0e1623,#0c1420);color:#b9c7d6;font-size:12px;text-transform:uppercase;letter-spacing:.8px;"
            "border-bottom:1px solid var(--line);padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center}"
            "td{padding:10px;border-bottom:1px solid var(--line2);font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center}"
            "thead th:not(:last-child), td:not(:last-child){border-right:1px solid #2a3b50}"
            ".pill{display:inline-flex;align-items:center;gap:6px;padding:5px 10px;border-radius:999px;border:1px solid #2a3b50;font-weight:800;font-size:12px}"
            ".pill .p{width:8px;height:8px;border-radius:50%}.on .p{background:var(--neon);box-shadow:0 0 10px var(--neon)}.off .p{background:#7b8391}"
            ".mono{font-variant-ligatures:none;font-family:inherit;color:#cfe8ff}.faint{color:var(--muted)}"
            ".groupTitle{margin:6px 2px 6px;font-weight:800;color:#b9c7d6;letter-spacing:.6px}"
            ".table::-webkit-scrollbar{height:10px;width:10px}.table::-webkit-scrollbar-track{background:#0e1623;border-radius:8px}"
            ".table::-webkit-scrollbar-thumb{background:#1e2a3f;border:1px solid #2a3b50;border-radius:8px}"
            ".table::-webkit-scrollbar-thumb:hover{background:#24334a;box-shadow:0 0 10px rgba(0,255,200,.25)}"
            ".table{scrollbar-width:thin;scrollbar-color:#1e2a3f #0e1623}"
            "@media (max-width:800px){.input input{width:80px}h2{font-size:22px}}"
            "</style></head><body><div class=\"wrap\">"
            "<h2>Connected Devices</h2>"
            "<div class=\"bar\">"
            "<button class=\"btn\" onclick=\"loadNow()\">Refresh</button>"
            "<label class=\"toggle\"><input id=\"detailToggle\" type=\"checkbox\" onchange=\"onToggleDetails()\"><span class=\"knob\"></span><span class=\"lbl\">Details</span></label>"
            "<label class=\"toggle\"><input id=\"groupToggle\" type=\"checkbox\" onchange=\"onToggleGroup()\"><span class=\"knob\"></span><span class=\"lbl\">Group</span></label>"
            "<span class=\"input\"><input id=\"appFilter\" placeholder=\"Filter App IDs (a,b,c)\"/><span class=\"hint\">? apply</span></span>"
            /* NEW: Auto Refresh control */
            "<span class=\"input\" title=\"Auto refresh interval\">"
            "<select id=\"refreshSel\">"
            "<option value=\"0\">Off</option>"
            "<option value=\"1000\">1s</option>"
            "<option value=\"3000\" selected>3s</option>"
            "<option value=\"5000\">5s</option>"
            "<option value=\"10000\">10s</option>"
            "<option value=\"30000\">30s</option>"
            "</select>"
            "<span class=\"hint\">auto</span>"
            "</span>"
            "<span class=\"input\" title=\"Custom seconds\">"
            "<input id=\"refreshIn\" type=\"number\" min=\"0\" step=\"1\" placeholder=\"sec\"/>"
            "<button class=\"btn\" style=\"padding:6px 10px\" id=\"refreshBtn\">Set</button>"
            "</span>"
            "<span class=\"meta\" id=\"meta\"><span class=\"dot\"></span></span>"
            "</div>"

            "<div id=\"single\" class=\"table\"><table>"
            "<colgroup id=\"colset\"></colgroup>"
            "<thead id=\"thead\"></thead><tbody id=\"rows\"><tr><td class=\"faint\">Loading...</td></tr></tbody></table></div>"

            "<div id=\"groups\"></div>"
            "</div>"
            "<script>"
            "const meta=document.getElementById('meta'),rows=document.getElementById('rows'),thead=document.getElementById('thead');"
            "const colset=document.getElementById('colset');const single=document.getElementById('single'),groups=document.getElementById('groups');"
            "const tDetails=document.getElementById('detailToggle'),tGroup=document.getElementById('groupToggle'),appFilter=document.getElementById('appFilter');"
            "const refreshSel=document.getElementById('refreshSel'),refreshIn=document.getElementById('refreshIn'),refreshBtn=document.getElementById('refreshBtn');"
            "const params=new URLSearchParams(location.search);"
            "let detailed=params.get('details')==='1'; let grouped=params.get('group')==='1';"
            "let filterList=(params.get('appIds')||'').split(',').map(s=>s.trim()).filter(Boolean);"
            "let refreshMs=Math.max(0,Number(params.get('refreshMs')||3000));"
            "tDetails.checked=detailed; tGroup.checked=grouped; appFilter.value=filterList.join(',');"
            "if([0,1000,3000,5000,10000,30000].includes(refreshMs)) refreshSel.value=String(refreshMs); else refreshSel.value='0';"
            "let latest=[]; let timer=null;"
            "function normMs(v){if(!v)return 0;const n=Number(v);if(!Number.isFinite(n))return 0;return n>1e12?n:n*1000}"
            "function fmt(ts){const ms=normMs(ts);if(!ms)return '';const d=new Date(ms);return Number.isNaN(d.getTime())?'':d.toLocaleString()}"
            "function passesFilter(it){if(!filterList.length)return true;const id=(it.appId||'').toString();return filterList.includes(id)}"
            "function setColgroup(cols){colset.innerHTML=cols.map(w=>'<col'+(w?' style=\\'width:'+w+'\\'':'')+'>').join('')}"
            "function setHeader(){if(grouped){return;} if(detailed){setColgroup(['18%','18%','26%','12%','12%','10%','']);thead.innerHTML='<tr><th>Device ID</th><th>App ID</th><th>Conn ID</th><th>IP</th><th>Start</th><th>Expiry</th><th>Online</th></tr>';}else{setColgroup(['55%','30%','']);thead.innerHTML='<tr><th>Device ID</th><th>App ID</th><th>Online</th></tr>';}}"
            "function pill(on){return '<span class=\\'pill '+(on?'on':'off')+'\\'><span class=\\'p\\'></span>'+(on?'ONLINE':'OFFLINE')+'</span>'}"
            "function cell(t){return '<td class=\\'mono\\'>'+t+'</td>'}"
            "function renderSingle(sample){single.style.display='block';groups.style.display='none';rows.innerHTML='';if(!sample.length){rows.innerHTML='<tr><td class=\\'faint\\' colspan=\\''+(detailed?7:3)+'\\'>No connections</td></tr>';return;}for(const it of sample){const tr=document.createElement('tr');const start=it.startMs??it.ts??0;const expC=it.expiryMs||it.exp||0;const exp=expC||(start?normMs(start)+86400000:0);if(detailed){tr.innerHTML=cell(it.deviceId||'')+cell(it.appId||'')+cell(it.connId||'')+cell(it.ip||'')+cell(fmt(start))+cell(fmt(exp))+cell(pill(!!it.online));}else{tr.innerHTML=cell(it.deviceId||'')+cell(it.appId||'')+cell(pill(!!it.online));}rows.appendChild(tr);}}"
            "function tableShell(headCols,colWidths,bodyHtml){return '<div class=\\'table\\'><table><colgroup>'+colWidths.map(w=>'<col'+(w?' style=\\'width:'+w+'\\'':'')+'>').join('')+'</colgroup><thead><tr>'+headCols.map(h=>'<th>'+h+'</th>').join('')+'</tr></thead><tbody>'+bodyHtml+'</tbody></table></div>'}"
            "function renderGroups(sample){single.style.display='none';groups.style.display='block';groups.innerHTML='';if(!sample.length){groups.innerHTML='<div class=\\'faint\\'>No connections</div>';return;}const map=new Map();for(const it of sample){const k=(it.appId||'');if(!map.has(k))map.set(k,[]);map.get(k).push(it);}const appIds=[...map.keys()].sort();for(const appId of appIds){const list=map.get(appId);let body='';if(detailed){for(const it of list){const start=it.startMs??it.ts??0;const expC=it.expiryMs||it.exp||0;const exp=expC||(start?normMs(start)+86400000:0);body+='<tr>'+cell(it.deviceId||'')+cell(it.connId||'')+cell(it.ip||'')+cell(fmt(start))+cell(fmt(exp))+cell(pill(!!it.online))+'</tr>'; }const head=['Device ID','Conn ID','IP','Start','Expiry','Online'];const title='<div class=\\'groupTitle\\'>APP ID: <span class=\\'mono\\'>'+(appId||'(unknown)')+'</span> � '+list.length+' device(s)</div>';groups.innerHTML+= title + tableShell(head,['25%','28%','12%','12%','13%',''],body);}else{for(const it of list){body+='<tr>'+cell(it.deviceId||'')+cell(pill(!!it.online))+'</tr>'; }const head=['Device ID','Online'];const title='<div class=\\'groupTitle\\'>APP ID: <span class=\\'mono\\'>'+(appId||'(unknown)')+'</span> � '+list.length+' device(s)</div>';groups.innerHTML+= title + tableShell(head,['70%',''],body);} } }"
            "function renderAll(){const sample=(latest||[]).filter(passesFilter);if(grouped){renderGroups(sample);}else{setHeader();renderSingle(sample);}}"

            /* Auto refresh logic */
            "function syncURL(){const p=new URLSearchParams(location.search);detailed?p.set('details','1'):p.delete('details');grouped?p.set('group','1'):p.delete('group');filterList.length?p.set('appIds',filterList.join(',')):p.delete('appIds');refreshMs?p.set('refreshMs',String(refreshMs)):p.delete('refreshMs');history.replaceState(null,'',location.pathname+(p.toString()?('?'+p.toString()):''));}"
            "function updateRefreshUI(){if([0,1000,3000,5000,10000,30000].includes(refreshMs)) refreshSel.value=String(refreshMs); else refreshSel.value='0';const sec=(refreshMs/1000)||0;const parts=meta.textContent.split(' � '); /* keep existing meta but rewrite auto= */ let base='count=?'; if(parts.length){base=parts[0].includes('count=')?parts[0]:('count=?');} meta.textContent=base+' � auto='+(refreshMs?sec+'s':'off')+' � updated '+new Date().toLocaleTimeString();}"
            "function applyRefresh(ms){if(timer){clearInterval(timer);timer=null;}refreshMs=Math.max(0,Number(ms)||0);if(refreshMs>0){timer=setInterval(loadNow,refreshMs);}syncURL();updateRefreshUI();}"
            "refreshSel.addEventListener('change',()=>{applyRefresh(Number(refreshSel.value));});"
            "refreshBtn.addEventListener('click',()=>{const v=Math.max(0,Math.floor(Number(refreshIn.value||0)));applyRefresh(v*1000);});"
            "refreshIn.addEventListener('keydown',e=>{if(e.key==='Enter'){const v=Math.max(0,Math.floor(Number(refreshIn.value||0)));applyRefresh(v*1000);}});"

            "async function loadNow(){try{const res=await fetch('/connections');const j=await res.json().catch(()=>({}));latest=j.sample||[];meta.textContent='count='+(j.count||latest.length)+' � auto='+(refreshMs?((refreshMs/1000)+'s'):'off')+' � updated '+new Date().toLocaleTimeString();renderAll();}catch(e){meta.textContent='failed to load';}}"
            "function onToggleDetails(){detailed=tDetails.checked;syncURL();renderAll();}"
            "function onToggleGroup(){grouped=tGroup.checked;syncURL();renderAll();}"
            "appFilter.addEventListener('keydown',e=>{if(e.key==='Enter'){filterList=appFilter.value.split(',').map(s=>s.trim()).filter(Boolean);syncURL();renderAll();}});"

            "renderAll();applyRefresh(refreshMs||3000);loadNow();"
            "</script>"
            "</body></html>";
        respondText(res, 200, html, "text/html; charset=utf-8");
        });









    bool listeningOk = false;
    app.listen(cfg.port, [&listeningOk, cfg](auto* token) {
        if (token) {
            listeningOk = true;
            std::cout << "Listening on :" << cfg.port << (cfg.useSsl ? " (TLS)" : "") << "\n";
        } else {
            std::cerr << "Failed to bind :" << cfg.port << "\n";
        }
    });

    if (!listeningOk) return 1;

    app.run();
    return 0;
}

} // namespace

int main() {
    // Load .env if available (from CWD or alongside the executable)
    loadDefaultDotEnv();

    ServerConfig cfg = loadConfig();
    if (cfg.authPublicPem.empty()) {
        std::cerr << "AUTH_PUBLIC_PEM is required" << std::endl;
        return 1;
    }
    if (cfg.rabbitUrl.empty()) {
        std::cerr << "RABBIT_URL is required" << std::endl;
        return 1;
    }
    if (cfg.useSsl && (cfg.sslKeyFile.empty() || cfg.sslCertFile.empty())) {
        std::cerr << "SSL_KEY and SSL_CERT are required when SSL is enabled" << std::endl;
        return 1;
    }

    if (cfg.useSsl) {
        return runServer<true>(cfg);
    }
    return runServer<false>(cfg);
}
