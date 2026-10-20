import type { Copy } from '../api/types';

/**
 * Which controls to show for a copy. This only hides buttons the backend would refuse
 * (a reserved copy is frozen); the backend stays the authority.
 */
export function copyActions(copy: Copy) {
  const reserved = copy.status === 'RESERVED';
  return {
    canChangeGrade: !reserved,
    canList: copy.status === 'UNLISTED',
    canUnlist: copy.status === 'AVAILABLE',
    canRemove: !reserved,
  };
}
