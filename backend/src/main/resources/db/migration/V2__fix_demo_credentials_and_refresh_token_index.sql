UPDATE app_user
SET password_hash = '$2a$10$rOYkY4SDjRpgivsn3q.H2e9kHJiuzsIOZ7TFs2ftQtp5.Ds2/5PNq'
WHERE username = 'demo_user';

UPDATE app_user
SET password_hash = '$2a$10$mSZ1DiQ2EZOlNhsPkL1ly.rGrypl0GW4N1t3Tg.B43d54TSCLIsQ.'
WHERE username = 'demo_admin';

CREATE UNIQUE INDEX uk_refresh_token_token_hash ON refresh_token(token_hash);
