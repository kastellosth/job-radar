/** Links come from outside APIs: only http(s) may become a link, never e.g. "javascript:". */
export function safeUrl(url: string): string | undefined {
  try {
    const protocol = new URL(url).protocol
    return protocol === 'http:' || protocol === 'https:' ? url : undefined
  } catch {
    return undefined
  }
}
