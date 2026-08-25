const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const ROOT = 'D:\\Harness Project\\TubeTune';
const PORT = 18099;
const server = http.createServer((req, res) => {
  try {
    const urlPath = decodeURIComponent((req.url || '/').split('?')[0]);
    let filePath = path.normalize(path.join(ROOT, urlPath));
    if (!filePath.startsWith(ROOT)) { res.writeHead(403); res.end('forbidden'); return; }
    if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) { res.writeHead(404); res.end('not found: ' + urlPath); return; }
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': fs.statSync(filePath).size,
      'Content-Disposition': 'attachment; filename="' + path.basename(filePath) + '"',
      'Access-Control-Allow-Origin': '*'
    });
    fs.createReadStream(filePath).pipe(res);
  } catch (e) {
    res.writeHead(500); res.end(String(e));
  }
});
server.listen(PORT, '0.0.0.0', () => console.log('APK server on ' + PORT));
