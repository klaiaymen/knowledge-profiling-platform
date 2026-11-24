import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { takeSseEvents } from './sse';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  get<T>(url: string): Observable<T> { return this.http.get<T>(url); }
  post<T>(url: string, body: unknown): Observable<T> { return this.http.post<T>(url, body); }
  delete(url: string): Observable<void> { return this.http.delete<void>(url); }

  stream<T>(url: string, body?: unknown): Observable<T> {
    return new Observable<T>(subscriber => {
      const controller = new AbortController();
      const headers: Record<string, string> = { Accept: 'text/event-stream' };
      const token = sessionStorage.getItem('kpf-token');
      if (token) headers['Authorization'] = `Bearer ${token}`;
      if (body !== undefined) headers['Content-Type'] = 'application/json';
      fetch(url, { method: body === undefined ? 'GET' : 'POST', headers, body: body === undefined ? undefined : JSON.stringify(body), signal: controller.signal })
        .then(async response => {
          if (!response.ok) throw new Error(await response.text() || `Request failed (${response.status})`);
          if (!response.body) throw new Error('Streaming response is unavailable');
          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          while (true) {
            const chunk = await reader.read();
            buffer += decoder.decode(chunk.value, { stream: !chunk.done });
            const parsed = takeSseEvents(buffer);
            buffer = parsed.remainder;
            for (const data of parsed.events) subscriber.next(JSON.parse(data) as T);
            if (chunk.done) break;
          }
          if (buffer.startsWith('data:')) subscriber.next(JSON.parse(buffer.slice(5).trim()) as T);
          subscriber.complete();
        })
        .catch((error: unknown) => { if (!controller.signal.aborted) subscriber.error(error); });
      return () => controller.abort();
    });
  }
}
