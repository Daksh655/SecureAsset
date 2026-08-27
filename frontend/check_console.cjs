const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch({ headless: 'new' });
  const page = await browser.newPage();
  
  page.on('console', msg => console.log('BROWSER CONSOLE:', msg.text()));
  page.on('pageerror', err => console.log('BROWSER ERROR:', err.toString()));
  page.on('requestfailed', req => console.log('REQUEST FAILED:', req.url(), req.failure().errorText));

  await page.goto('http://[::1]:5173/cases', { waitUntil: 'networkidle2', timeout: 10000 }).catch(e => console.log('Navigation timeout or error', e));
  
  await browser.close();
})();
