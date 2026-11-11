import { readStorage, writeStorage, TOKEN_STORAGE_KEY } from '../platform/storage.ts'

let token = readStorage(TOKEN_STORAGE_KEY)
export function readToken(): string | null { return token }
export function writeToken(value: string | null): void {
  token = value
  writeStorage(TOKEN_STORAGE_KEY, value)
}
