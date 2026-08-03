import { chromium } from 'playwright';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const outputs = [
  { html: 'kit-list.html', png: 'kit-list.png', width: 1920, height: 1080 },
  { html: 'kit-detail.html', png: 'kit-detail.png', width: 1920, height: 1080 },
  { html: 'kit-flow.html', png: 'kit-flow.png', width: 1920, height: 1080 },
];

const browser = await chromium.launch();
for (const item of outputs) {
  const page = await browser.newPage({
    viewport: { width: item.width, height: item.height },
    deviceScaleFactor: 2,
  });
  const filePath = path.join(__dirname, item.html);
  await page.goto(`file:///${filePath.replace(/\\/g, '/')}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);
  await page.screenshot({
    path: path.join(__dirname, item.png),
    fullPage: false,
  });
  await page.close();
  console.log(`Created ${item.png}`);
}
await browser.close();
