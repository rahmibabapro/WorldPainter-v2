#!/usr/bin/env node

/**
 * WorldPainter MCP server — stdio JSON-RPC to the live bridge at 127.0.0.1:8765.
 */

import http from "node:http";
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

const BRIDGE_URL = process.env.WP_BRIDGE_URL || "http://127.0.0.1:8765";
const BRIDGE_TOKEN = process.env.WP_BRIDGE_TOKEN || "";

async function makeRequest(path, method = "GET", body = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BRIDGE_URL);
    const headers = { "Content-Type": "application/json" };
    if (BRIDGE_TOKEN) headers["X-WP-Token"] = BRIDGE_TOKEN;

    const req = http.request(
      {
        hostname: url.hostname,
        port: url.port,
        path: url.pathname + url.search,
        method,
        headers,
      },
      (res) => {
        let data = "";
        res.on("data", (chunk) => {
          data += chunk;
        });
        res.on("end", () => {
          try {
            resolve(JSON.parse(data));
          } catch {
            resolve({ raw: data, status: res.statusCode });
          }
        });
      }
    );

    req.on("error", (err) => {
      reject(
        new Error(
          `WorldPainter Bridge bağlantı hatası (${BRIDGE_URL}). WorldPainter v2 açık mı? Detay: ${err.message}`
        )
      );
    });

    if (body) {
      req.write(typeof body === "string" ? body : JSON.stringify(body));
    }
    req.end();
  });
}

const TOOLS = [
  {
    name: "wp_get_world_info",
    description:
      "WorldPainter'da açık olan aktif dünyanın adını, sınırlarını, su seviyesini, katman listesini ve dosya yolunu getirir.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "wp_inspect_region",
    description:
      "Belirtilen koordinat aralığındaki arazinin yükseklik değerlerini, ortalama/min/max rakımlarını ve su durumunu inceler.",
    inputSchema: {
      type: "object",
      properties: {
        minX: { type: "number", description: "Başlangıç X (blok)" },
        minY: { type: "number", description: "Başlangıç Y (blok)" },
        maxX: { type: "number", description: "Bitiş X (blok)" },
        maxY: { type: "number", description: "Bitiş Y (blok)" },
        step: { type: "number", description: "Örnekleme aralığı (varsayılan: 8)", default: 8 },
      },
      required: ["minX", "minY", "maxX", "maxY"],
    },
  },
  {
    name: "wp_create_river",
    description:
      "Verilen koordinat noktaları arasında doğal A* vadi koridoru ve hidrolojik basamaklı nehir yatağı oyar.",
    inputSchema: {
      type: "object",
      properties: {
        points: {
          type: "array",
          items: {
            type: "object",
            properties: { x: { type: "number" }, y: { type: "number" } },
            required: ["x", "y"],
          },
          description: "En az 2 nokta: kaynak ve çıkış",
        },
        width: { type: "number", description: "Kanal genişliği (varsayılan: 8)", default: 8 },
        depth: { type: "number", description: "Kazı derinliği (varsayılan: 3)", default: 3 },
        smooth: { type: "boolean", description: "Kıyı yumuşatma", default: true },
        granite: { type: "boolean", description: "Sığ granit detayı", default: true },
        mode: {
          type: "string",
          enum: ["auto", "preserve", "adapt"],
          default: "auto",
          description:
            "auto: önce arazi korumalı (preserve), olmazsa adaptasyon; kuru haritada genelde adapt gerekir",
        },
      },
      required: ["points"],
    },
  },
  {
    name: "wp_sculpt_terrain",
    description:
      "Merkez koordinatta dağ/tepe/vadi/plato için RAISE|LOWER|SMOOTH|PLATEAU kosinüs geçişli şekillendirme.",
    inputSchema: {
      type: "object",
      properties: {
        x: { type: "number" },
        y: { type: "number" },
        radius: { type: "number" },
        amount: { type: "number", default: 20 },
        mode: {
          type: "string",
          enum: ["RAISE", "LOWER", "SMOOTH", "PLATEAU"],
          default: "RAISE",
        },
      },
      required: ["x", "y", "radius"],
    },
  },
  {
    name: "wp_apply_biome_layer",
    description:
      "Eğim (derece) ve yükseklik filtreleriyle terrain veya BIT katman boyama. Örn: minSlope 45 = dik kayalık.",
    inputSchema: {
      type: "object",
      properties: {
        minX: { type: "number" },
        minY: { type: "number" },
        maxX: { type: "number" },
        maxY: { type: "number" },
        terrain: { type: "string", description: "GRASS, STONE, SAND, …" },
        layer: { type: "string" },
        minSlope: { type: "number", description: "Min eğim derecesi 0–90", default: 0 },
        maxSlope: { type: "number", description: "Max eğim derecesi 0–90", default: 90 },
        minHeight: { type: "number" },
        maxHeight: { type: "number" },
      },
      required: ["minX", "minY", "maxX", "maxY"],
    },
  },
  {
    name: "wp_run_script",
    description:
      "WorldPainter JS motorunda kod çalıştırır (app, world, dimension bağlı). Hata olursa undo.",
    inputSchema: {
      type: "object",
      properties: {
        code: { type: "string" },
      },
      required: ["code"],
    },
  },
  {
    name: "wp_undo",
    description: "Son WorldPainter işlemini geri alır.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "wp_redo",
    description: "Geri alınan işlemi yineler.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "wp_save_world",
    description:
      "Açık dünyayı bilinen .world dosyasına dialog açmadan kaydeder. Önce UI'dan bir kez kaydedilmiş olmalı.",
    inputSchema: { type: "object", properties: {} },
  },
];

async function handleToolCall(name, args = {}) {
  switch (name) {
    case "wp_get_world_info":
      return await makeRequest("/api/world");
    case "wp_inspect_region":
      return await makeRequest(
        `/api/inspect?minX=${args.minX}&minY=${args.minY}&maxX=${args.maxX}&maxY=${args.maxY}&step=${args.step || 8}`
      );
    case "wp_create_river":
      return await makeRequest("/api/river/carve", "POST", args);
    case "wp_sculpt_terrain":
      return await makeRequest("/api/terrain/sculpt", "POST", args);
    case "wp_apply_biome_layer":
      return await makeRequest("/api/terrain/paint", "POST", args);
    case "wp_run_script":
      return await makeRequest("/api/script", "POST", args);
    case "wp_undo":
      return await makeRequest("/api/undo", "POST");
    case "wp_redo":
      return await makeRequest("/api/redo", "POST");
    case "wp_save_world":
      return await makeRequest("/api/save", "POST", {});
    default:
      throw new Error(`Bilinmeyen araç: ${name}`);
  }
}

const server = new Server(
  { name: "worldpainter-mcp-server", version: "1.1.0" },
  { capabilities: { tools: {} } }
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: TOOLS }));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const name = request.params.name;
  const args = request.params.arguments || {};
  try {
    const result = await handleToolCall(name, args);
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      isError: result && result.success === false,
    };
  } catch (err) {
    return {
      isError: true,
      content: [{ type: "text", text: `Hata: ${err.message}` }],
    };
  }
});

const transport = new StdioServerTransport();
await server.connect(transport);
