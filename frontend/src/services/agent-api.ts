import { http } from './http';
import { getSession } from './session';
import type { ApiResponse } from '../types/auth';
import type {
  AgentMessageRequest,
  AgentStreamEvent,
  AssistantStatus,
  ConversationView,
  InvocationLog,
} from '../types/agent';

async function data<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  return (await promise).data.data;
}

export const getAgentStatus = (): Promise<AssistantStatus> => data(http.get('/agent/status'));
export const createConversation = (title: string): Promise<ConversationView> =>
  data(http.post('/agent/conversations', { title }));
export const getConversations = (): Promise<ConversationView[]> =>
  data(http.get('/agent/conversations'));
export const confirmDraft = (id: string): Promise<void> =>
  data(http.put(`/agent/drafts/${id}/confirmed`));
export const getInvocationLogs = (): Promise<InvocationLog[]> =>
  data(http.get('/admin/agent/invocations'));

export async function streamMessage(
  conversationId: string,
  request: AgentMessageRequest,
  onEvent: (event: AgentStreamEvent) => void,
): Promise<void> {
  const token = getSession()?.accessToken;
  const response = await fetch(`/api/agent/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(request),
  });
  if (!response.ok || !response.body) throw new Error(`智能助手连接失败（${response.status}）`);
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value, { stream: !done });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() ?? '';
    for (const block of blocks) {
      const eventName = block.match(/^event:(.+)$/m)?.[1]?.trim() ?? 'message';
      const rawData = block
        .split(/\r?\n/)
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trim())
        .join('\n');
      if (!rawData) continue;
      let parsed: unknown = rawData;
      try {
        parsed = JSON.parse(rawData);
      } catch {
        // Spring SSE 也可能发送纯文本；保留原始内容。
      }
      onEvent({ name: eventName, data: parsed });
    }
    if (done) break;
  }
}
