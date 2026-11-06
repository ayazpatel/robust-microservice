// RabbitMQ publisher (singleton channel)
import amqp from "amqplib";

let channel;

/**
 * initRabbit
 * @param {string} url amqp(s)://user:pass@host:5672/vhost
 * @param {string} exchange topic exchange name
 */
export async function initRabbit(url, exchange) {
  const conn = await amqp.connect(url);
  conn.on("error", (e) => console.error("[AMQP] conn error", e.message));
  conn.on("close", () => console.error("[AMQP] connection closed"));
  channel = await conn.createChannel();
  await channel.assertExchange(exchange, "topic", { durable: true });
  console.log("[AMQP] ready");
}

/**
 * publish JSON to exchange/routingKey
 */
export function publishJSON(exchange, routingKey, obj) {
  if (!channel) return false;
  const buf = Buffer.from(JSON.stringify(obj));
  return channel.publish(exchange, routingKey, buf, {
    contentType: "application/json",
    deliveryMode: 2 // persistent
  });
}
