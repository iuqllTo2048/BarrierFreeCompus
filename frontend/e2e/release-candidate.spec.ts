import { expect, test, type Page } from '@playwright/test';

async function login(page: Page, role: 'USER' | 'ADMIN' = 'USER'): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('用户名').fill(role === 'ADMIN' ? 'demo_admin' : 'demo_user');
  await page.getByLabel('密码').fill(role === 'ADMIN' ? 'Admin@12345' : 'Demo@12345');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(role === 'ADMIN' ? /\/admin$/ : /\/user$/);
}

async function waitForBlankSchoolDataset(page: Page): Promise<void> {
  const routePage = page.getByLabel('规划校园通行路线');
  await expect(routePage).toContainText('学校示例校园数据集');
  await expect(routePage).toContainText('起点从地图选择请选择');
  await expect(routePage).not.toContainText('西北门（N-01）');
}

test('USER 默认进入新学校空白数据集且看不到停用的旧 Demo', async ({ page }) => {
  await login(page);
  await waitForBlankSchoolDataset(page);
  await expect(page.getByRole('option', { name: /云麓校园演示数据/ })).toHaveCount(0);
});

test('ADMIN 可查看停用旧数据并使用折线拐点撤销操作', async ({ page }) => {
  await login(page, 'ADMIN');
  await page.getByRole('combobox', { name: '管理数据集' }).press('ArrowDown');
  await page.getByRole('option', { name: '云麓校园演示数据（已停用）' }).click();
  await expect(page.getByRole('toolbar', { name: '地图编辑工具' })).toContainText(/[1-9]\d* 节点/);

  await page.locator('#object-select').click();
  await page.getByRole('option', { name: '道路 · 中央连廊' }).click();
  await expect(page.getByText('1 个拐点；起终点固定在道路节点')).toBeVisible();
  await page.getByRole('button', { name: '添加拐点' }).click();
  await expect(page.getByText('2 个拐点；起终点固定在道路节点')).toBeVisible();
  await page.getByRole('button', { name: '撤销上一步', exact: true }).click();
  await expect(page.getByText('1 个拐点；起终点固定在道路节点')).toBeVisible();
});

test('ADMIN 可预检并安全合并 Formal GeoJSON', async ({ page }) => {
  await login(page, 'ADMIN');
  const payload = {
    type: 'FeatureCollection',
    schemaVersion: 2,
    datasetId: '20000000-0000-0000-0000-000000000002',
    datasetCode: 'SCHOOL_EXAMPLE_V1',
    coordinateSystem: 'GCJ02',
    exportedAt: new Date().toISOString(),
    features: [],
  };
  await page.locator('input[type="file"]').setInputFiles({
    name: 'school-example.geojson',
    mimeType: 'application/geo+json',
    buffer: Buffer.from(JSON.stringify(payload)),
  });

  const previewDialog = page.getByRole('dialog', { name: 'GeoJSON 安全导入预览' });
  await expect(previewDialog).toBeVisible();
  await expect(previewDialog.locator('.merge-safety-note')).toContainText(
    '文件中缺失的本地对象不会被删除',
  );
  await page.getByRole('button', { name: '确认合并导入' }).click();
  await expect(page.getByText(/导入完成：新增 0、更新 0/)).toBeVisible();
});

test('用户提交的脚本文本只按普通文字展示', async ({ page }) => {
  const xssTitle = `<img src=x onerror=alert(1)> ${Date.now()}`;
  const reportLng = (104.690359 + Math.random() * 0.01).toFixed(7);
  const reportLat = (31.529827 + Math.random() * 0.01).toFixed(7);
  await login(page);
  await page.getByRole('link', { name: '用户服务' }).click();
  await page.getByRole('tab', { name: '障碍上报' }).click();
  await page.locator('#barrier-title').fill(xssTitle);
  await page.locator('#barrier-description').fill('<script>window.__xss=1</script> 测试上报');
  const coordinates = page.locator('.coordinate-grid input');
  await coordinates.nth(0).fill(reportLng);
  await coordinates.nth(1).fill(reportLat);
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

test('智能助手状态可见且手工路线入口不依赖外部模型', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: '智能路线助手' }).click();
  await expect(page.getByText(/真实模型|本地演示模式/)).toBeVisible();
  await expect(
    page.getByText('AI 负责理解与解释，路线和风险始终来自后端白名单工具。'),
  ).toBeVisible();
  await page.getByRole('link', { name: '路线规划' }).click();
  await waitForBlankSchoolDataset(page);
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
