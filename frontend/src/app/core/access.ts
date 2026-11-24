export type Role = 'ROLE_USER' | 'ROLE_ADMIN';

export function rolesFromJwt(token: string | null): Role[] {
  if (!token) return [];
  try {
    const payload = token.split('.')[1];
    if (!payload) return [];
    const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as { authorities?: unknown };
    if (!Array.isArray(decoded.authorities)) return [];
    return decoded.authorities.filter((role): role is Role => role === 'ROLE_USER' || role === 'ROLE_ADMIN');
  } catch {
    return [];
  }
}

export function allowsRole(roles: readonly Role[], required: Role): boolean {
  return roles.includes('ROLE_ADMIN') || roles.includes(required);
}
