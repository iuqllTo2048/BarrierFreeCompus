import { describe, expect, it } from 'vitest';
import {
  buildingScoreOption,
  confidenceOption,
  trendOption,
  type ChartPalette,
} from './analytics-charts';

const palette: ChartPalette = {
  primary: '#0f766e',
  secondary: '#176b82',
  success: '#067647',
  warning: '#b54708',
  danger: '#b42318',
  unknown: '#667085',
  text: '#18332d',
  muted: '#52645f',
  border: '#d5e2de',
};

describe('analytics chart options', () => {
  it('uses sorted score labels and data-sufficiency styling', () => {
    const option = buildingScoreOption(
      [
        {
          id: '1',
          name: '图书馆',
          score: 82,
          entranceScore: 20,
          elevatorScore: 15,
          toiletScore: 15,
          roadScore: 20,
          barrierScore: 5,
          completenessScore: 7,
          dataSufficient: false,
          reasons: ['数据不足'],
          lng: 112.9,
          lat: 28.1,
        },
      ],
      palette,
    );

    expect(option.aria).toMatchObject({ enabled: true });
    expect(option.yAxis).toMatchObject({ data: ['图书馆'] });
    expect(option.series).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ type: 'bar', data: [expect.objectContaining({ value: 82 })] }),
      ]),
    );
  });

  it('distinguishes trend series with names, symbols and line styles', () => {
    const option = trendOption([{ date: '2026-08-19', submitted: 2, approved: 1 }], palette);
    expect(option.series).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ name: '新增上报', type: 'line', symbol: 'circle' }),
        expect.objectContaining({ name: '审核通过', type: 'line', symbol: 'diamond' }),
      ]),
    );
  });

  it('keeps explicit confidence labels instead of color-only categories', () => {
    const option = confidenceOption(
      [{ entityType: 'EDGE', entityLabel: '道路', high: 1, medium: 2, low: 3, unknown: 4 }],
      palette,
    );
    expect(option.legend).toMatchObject({ data: ['HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'] });
  });
});
