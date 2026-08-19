import { expect, test, type Page } from '@playwright/test';

async function login(page: Page, role: 'USER' | 'ADMIN' = 'USER'): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('用户名').fill(role === 'ADMIN' ? 'demo_admin' : 'demo_user');
  await page.getByLabel('密码').fill(role === 'ADMIN' ? 'Admin@12345' : 'Demo@12345');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(role === 'ADMIN' ? /\/admin$/ : /\/user$/);
}

async function waitForRouteForm(page: Page): Promise<void> {
  const routePage = page.getByLabel('规划校园通行路线');
  await expect(routePage).toContainText('西北门（N-01）');
  await expect(routePage).toContainText('东南门（N-20）');
}

test('USER 登录后可规划三类路线并看到风险文字', async ({ page }) => {
  await login(page);
  await waitForRouteForm(page);
  await page.getByRole('button', { name: '规划三类路线' }).click();
  const results = page.getByRole('complementary', { name: '路线规划结果' });
  await expect(results).toContainText('条候选路线');
  await expect(results).toContainText(/最短路线|无障碍优先|综合路线/);
  await expect(results).toContainText(/风险/);
});

test('轮椅模式规划结果不包含楼梯通行并保留路线比较', async ({ page }) => {
  await login(page);
  await waitForRouteForm(page);
  await page.getByLabel('规划校园通行路线').getByText('普通步行', { exact: true }).click();
  await page.getByRole('option', { name: '轮椅出行' }).click();
  await page.getByRole('button', { name: '规划三类路线' }).click();
  const results = page.getByRole('complementary', { name: '路线规划结果' });
  await expect(results).toContainText('条候选路线');
  await expect(results).toContainText(/无障碍优先|综合路线/);
  await expect(results).not.toContainText('楼梯 1');
});

test('用户提交的脚本文本只按普通文字展示', async ({ page }) => {
  const xssTitle = `<img src=x onerror=alert(1)> ${Date.now()}`;
  await login(page);
  await page.getByRole('link', { name: '用户服务' }).click();
  await page.getByRole('tab', { name: '障碍上报' }).click();
  await page.locator('#barrier-title').fill(xssTitle);
  await page.locator('#barrier-description').fill('<script>window.__xss=1</script> 测试上报');
  const coordinates = page.locator('.coordinate-grid input');
  await coordinates.nth(0).fill('112.936300');
  await coordinates.nth(1).fill('28.177800');
  await page.getByRole('button', { name: '提交上报' }).click();
  await expect(page.getByText('障碍已提交，审核通过前不会影响路线')).toBeVisible();
  await page.getByRole('tab', { name: '我的上报' }).click();
  await expect(page.getByText(xssTitle)).toBeVisible();
  expect(await page.evaluate(() => (window as Window & { __xss?: number }).__xss)).toBeUndefined();
});

test('ADMIN 能进入治理工作台且权限页面完整加载', async ({ page }) => {
  await login(page, 'ADMIN');
  await page.getByRole('link', { name: '治理工作台' }).click();
  await expect(page.getByRole('heading', { name: '治理工作台' })).toBeVisible();
  await expect(page.getByRole('tab', { name: '障碍审核' })).toBeVisible();
  await expect(page.getByRole('button', { name: '安全重置 Demo' })).toBeVisible();
});

test('AI 外部调用关闭时使用确定性 Mock 且手工路线仍可使用', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: '智能路线助手' }).click();
  await page.getByLabel('路线、设施或障碍需求').fill('从图书馆到体育与健康中心，轮椅怎么走？');
  await page.getByRole('button', { name: '开始分析' }).click();
  await expect(page.getByText('本地演示模式')).toBeVisible();
  await expect(page.getByRole('list', { name: '业务工具执行进度' })).toContainText(
    '计算无障碍路线',
  );
  await expect(page.getByText('路线助手结论')).toBeVisible();
  await page.getByRole('link', { name: '路线规划' }).click();
  await waitForRouteForm(page);
  await page.getByRole('button', { name: '规划三类路线' }).click();
  await expect(page.getByRole('complementary', { name: '路线规划结果' })).toContainText(
    '条候选路线',
  );
});

test.describe('375px 移动端', () => {
  test.use({ viewport: { width: 375, height: 812 } });
  test('导航、主题和地图底部面板都有按钮替代操作', async ({ page }) => {
    await login(page);
    const menu = page.getByRole('button', { name: '打开主导航' });
    await expect(menu).toBeVisible();
    await menu.click();
    await expect(page.getByRole('navigation', { name: '移动端主导航' })).toBeVisible();
    await page.getByRole('button', { name: '关闭主导航' }).click();
    await page.getByRole('button', { name: '切换到深色模式' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    const routeSettings = page.getByRole('button', { name: /路线设置/ });
    await expect(routeSettings).toHaveAttribute('aria-expanded', 'true');
    await routeSettings.click();
    await expect(routeSettings).toHaveAttribute('aria-expanded', 'false');
  });
});
