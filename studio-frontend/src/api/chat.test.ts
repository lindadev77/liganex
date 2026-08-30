import { describe, expect, it, vi } from 'vitest';
import { consumeSseStream, parseChatMessage, parseSseBlock } from './chat';

function streamOf(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

describe('SSE parser', () => {
  it('parses comments and multi-line data', () => {
    expect(parseSseBlock(': keepalive\nevent: token\ndata: hello\ndata: world')).toEqual({
      event: 'token',
      data: 'hello\nworld',
    });
  });

  it('streams token increments and a done event split across chunks', async () => {
    const onToken = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();
    await consumeSseStream(
      streamOf(
        'event: token\ndata: {"content":"你',
        '好"}\n\nevent: done\ndata: {"messageId":"m-1","citations":[]}\n\n',
      ),
      { onToken, onDone, onError },
    );
    expect(onToken).toHaveBeenCalledWith({ type: 'token', content: '你好' });
    expect(onDone).toHaveBeenCalledWith({
      type: 'done',
      messageId: 'm-1',
      content: undefined,
      citations: [],
    });
    expect(onError).not.toHaveBeenCalled();
  });

  it('handles an explicit error event', async () => {
    const onError = vi.fn();
    await consumeSseStream(
      streamOf('event: error\ndata: {"code":"MODEL_TIMEOUT","message":"模型超时"}\n\n'),
      { onToken: vi.fn(), onDone: vi.fn(), onError },
    );
    expect(onError).toHaveBeenCalledWith({
      type: 'error',
      code: 'MODEL_TIMEOUT',
      message: '模型超时',
    });
  });

  it('rejects a connection closed before done or error', async () => {
    await expect(
      consumeSseStream(streamOf('event: token\ndata: {"content":"partial"}\n\n'), {
        onToken: vi.fn(),
        onDone: vi.fn(),
        onError: vi.fn(),
      }),
    ).rejects.toThrow('回答连接意外中断');
  });
});

describe('chat response parser', () => {
  it('normalizes the backend message and citation snapshot', () => {
    expect(
      parseChatMessage({
        id: 10,
        conversationId: 3,
        role: 'ASSISTANT',
        content: '回答',
        status: 'COMPLETED',
        citations: JSON.stringify([
          {
            documentId: 5,
            chunkId: 'chunk-1',
            sourceName: '手册.pdf',
            excerpt: '引用内容',
            startOffset: 10,
            endOffset: 20,
            available: true,
          },
        ]),
        createdAt: '2026-08-30T00:00:00Z',
      }),
    ).toMatchObject({
      id: '10',
      conversationId: '3',
      role: 'assistant',
      status: 'completed',
      citations: [{ documentId: '5', documentName: '手册.pdf', available: true }],
    });
  });
});
