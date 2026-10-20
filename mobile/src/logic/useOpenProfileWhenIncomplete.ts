import { useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { getMe } from '../api/endpoints/profile';
import { useApi } from '../auth/session';

/** Right after sign-up the profile is empty: open the Profile tab once so the user fills it in. */
export function useOpenProfileWhenIncomplete() {
  const api = useApi();
  const router = useRouter();
  const done = useRef(false);
  const me = useQuery({ queryKey: ['me'], queryFn: () => getMe(api) });
  useEffect(() => {
    if (me.data && !me.data.profileComplete && !done.current) {
      done.current = true;
      router.replace('/profile');
    }
  }, [me.data, router]);
}
