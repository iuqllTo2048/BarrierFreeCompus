import { afterEach, describe, expect, it } from 'vitest';
import { clearSession, getSession, setSession } from './session';

describe('内存会话', () => {
  afterEach(clearSession);

  it('只在进程内保存访问令牌并可清除', () => {
    setSession({ username: 'demo_user', role: 'USER', accessToken: 'token' });
    expect(getSession()).toEqual({ username: 'demo_user', role: 'USER', accessToken: 'token' });
    clearSession();
    expect(getSession()).toBeNull();
  });
});
