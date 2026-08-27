const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch({ headless: 'new' });
  const page = await browser.newPage();
  
  page.on('pageerror', err => console.log('PAGE ERROR:', err.toString()));
  
  await page.goto('http://[::1]:5173/recovery-cases/b8d9a175-32f2-42c5-a595-ccf544e60bbf', { waitUntil: 'networkidle2', timeout: 10000 }).catch(e => console.log('Navigation timeout or error', e));
  
  await page.evaluate(() => {
    const tabs = Array.from(document.querySelectorAll('.tab'));
    const paymentTab = tabs.find(t => t.textContent === 'Payment');
    if (paymentTab) paymentTab.click();
  });
  
  await new Promise(r => setTimeout(r, 1000));
  
  const text = await page.evaluate(() => {
    return document.body.innerHTML;
  });
  console.log('HTML length:', text.length);
  if (text.includes('detail-grid')) {
    console.log('Detail grid found!');
  } else {
    console.log('Excerpt:', text.substring(0, 500));
  }
  
  await browser.close();
})();
