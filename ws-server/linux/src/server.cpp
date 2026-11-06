#include <uwebsockets/App.h>
#include <iostream>
#include <string_view>

struct PerSocketData {};

int main() {
    // HTTP health endpoint + WS echo
    uWS::App()
        .get("/healthz", [](auto* res, auto*/*req*/) {
        res->writeStatus("200 OK")
            ->writeHeader("Content-Type", "text/plain")
            ->end("ok");
            })
        .ws<PerSocketData>("/*", {
            .open = [](auto* ws) {
                std::cout << "WS open, subscribed to 'room'\n";
                ws->subscribe("room");
            },
            .message = [](auto* ws, std::string_view msg, uWS::OpCode op) {
                // Echo back
                ws->send(msg, op);
            },
            .close = [](auto*/*ws*/, int code, std::string_view msg) {
                std::cout << "WS close " << code << " " << msg << "\n";
            }
            })
        .listen(9001, [](auto* sock) {
        if (sock) std::cout << "Listening on :9001\n";
        else std::cout << "Bind/permission error\n";
            })
        .run();

    std::cout << "Server stopped.\n";
    return 0;
}
