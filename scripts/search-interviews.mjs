const args = new Map();

for (let index = 2; index < process.argv.length; index += 2) {
  const key = process.argv[index]?.replace(/^--/, "");
  const value = process.argv[index + 1];
  if (key && value) {
    args.set(key, value);
  }
}

const position = args.get("position") || "Java";
const company = args.get("company") || "ByteDance";
const keywords = (args.get("keywords") || "Spring,Redis")
  .split(/[,，]/)
  .map((item) => item.trim())
  .filter(Boolean);
const size = Number(args.get("size") || 5);

const response = await fetch("http://127.0.0.1:8080/api/interview-experiences/search", {
  method: "POST",
  headers: {
    "Content-Type": "application/json; charset=utf-8"
  },
  body: JSON.stringify({
    position,
    company,
    keywords,
    page: 1,
    size
  })
});

if (!response.ok) {
  const text = await response.text();
  throw new Error(`搜索失败：${response.status}\n${text}`);
}

const data = await response.json();
const items = data.items.slice(0, size);

console.log("");
console.log(`搜索关键词：${data.query}`);
console.log(`结果数量：${data.total}`);
console.log("");

items.forEach((item, index) => {
  console.log(`${index + 1}. ${item.title}`);
  console.log(`   链接：${item.sourceUrl}`);
  const highlight = item.highlights?.[0];
  if (highlight) {
    console.log(`   摘要：${highlight}`);
  }
  console.log("");
});
