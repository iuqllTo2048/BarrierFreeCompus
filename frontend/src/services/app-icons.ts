export type AppIconName =
  | 'accessible-entrance'
  | 'analytics'
  | 'assistant'
  | 'barrier'
  | 'building'
  | 'close'
  | 'drop-off'
  | 'elevator'
  | 'governance'
  | 'map'
  | 'menu'
  | 'moon'
  | 'node'
  | 'parking'
  | 'ramp'
  | 'rest-area'
  | 'route'
  | 'services'
  | 'sun'
  | 'toilet'
  | 'transit'
  | 'warning';

export const appIconPaths: Record<AppIconName, string[]> = {
  'accessible-entrance': ['M4 21v-8a2 2 0 0 1 2-2h8', 'M14 4h6v17h-6z', 'M8 7h3', 'm9 12 2-2 2 2'],
  analytics: ['M4 20V10', 'M10 20V4', 'M16 20v-7', 'M22 20H2'],
  assistant: ['M8 9h8', 'M8 13h5', 'M5 19l-2 2v-4a8 8 0 1 1 4 3'],
  barrier: ['M5 21 19 3', 'M4 7h4', 'M16 7h4', 'M4 17h4', 'M16 17h4', 'M7 3h10l4 18H3z'],
  building: ['M4 21V5l8-3 8 3v16', 'M9 21v-4h6v4', 'M8 7h1', 'M15 7h1', 'M8 11h1', 'M15 11h1'],
  close: ['m6 6 12 12', 'M18 6 6 18'],
  'drop-off': ['M3 17h18', 'M5 17V9h10l4 4v4', 'M7 20h.01', 'M17 20h.01', 'M8 6h5'],
  elevator: ['M5 3h14v18H5z', 'm9 8 3-3 3 3', 'm9 16 3 3 3-3'],
  governance: ['M12 3 4 7v5c0 5 3.4 8.5 8 10 4.6-1.5 8-5 8-10V7z', 'm9 12 2 2 4-4'],
  map: ['m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3z', 'M9 3v15', 'M15 6v15'],
  menu: ['M4 7h16', 'M4 12h16', 'M4 17h16'],
  moon: ['M20 15.5A8 8 0 0 1 8.5 4 8.5 8.5 0 1 0 20 15.5z'],
  node: [
    'M12 7a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
    'M5 23a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
    'M19 23a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
    'm10 7-4 10',
    'm14 7 4 10',
  ],
  parking: ['M5 21V3h7a5 5 0 0 1 0 10H5', 'M5 13h7'],
  ramp: ['M3 19h18', 'M5 17 17 8v9'],
  'rest-area': ['M5 12h14v6H5z', 'M7 18v3', 'M17 18v3', 'M8 12V8h8v4'],
  route: [
    'M6 19a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
    'M18 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
    'M6 13V8c0-3 2-5 5-5h2c3 0 5 2 5 5',
  ],
  services: ['M4 6h16', 'M4 12h16', 'M4 18h10', 'M7 3v6', 'M17 9v6', 'M11 15v6'],
  sun: [
    'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z',
    'M12 2v2',
    'M12 20v2',
    'm4.93 4.93 1.42 1.42',
    'm17.66 17.66 1.41 1.41',
    'M2 12h2',
    'M20 12h2',
    'm6.34 17.66-1.41 1.41',
    'm19.07 4.93-1.41 1.41',
  ],
  toilet: [
    'M7 3a2 2 0 1 0 0 4 2 2 0 0 0 0-4Z',
    'M5 21v-7H3l2-5h4l2 5H9v7',
    'M16 3a2 2 0 1 0 0 4 2 2 0 0 0 0-4Z',
    'M13 9h6l2 6h-3v6h-4v-6h-3z',
  ],
  transit: ['M5 16V6c0-3 14-3 14 0v10', 'M4 16h16', 'M7 20h.01', 'M17 20h.01', 'M8 7h8'],
  warning: ['M12 3 2 21h20z', 'M12 9v5', 'M12 18h.01'],
};
