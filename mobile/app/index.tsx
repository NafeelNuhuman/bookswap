import { Redirect } from 'expo-router';
import { useSession } from '../src/auth/session';

/** The app opens at "/", which no screen owns: send the user to the right place for their session. */
export default function Index() {
  const { status } = useSession();
  return <Redirect href={status === 'signedIn' ? '/library' : '/login'} />;
}
