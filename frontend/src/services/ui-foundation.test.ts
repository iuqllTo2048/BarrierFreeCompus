import { describe, expect, it } from 'vitest';
import { appIconPaths } from './app-icons';
import { resolveTheme } from './theme';

describe('Stage 7 UI 基础', () => {
  it('优先使用用户保存的主题并回退到系统偏好', () => {
    expect(resolveTheme('light', true)).toBe('light');
    expect(resolveTheme('dark', false)).toBe('dark');
    expect(resolveTheme(null, true)).toBe('dark');
    expect(resolveTheme('invalid', false)).toBe('light');
  });

  it('所有项目图标都有可绘制的 SVG 路径', () => {
    for (const paths of Object.values(appIconPaths)) {
      expect(paths.length).toBeGreaterThan(0);
      expect(paths.every((path) => path.trim().length > 2)).toBe(true);
    }
  });
});
