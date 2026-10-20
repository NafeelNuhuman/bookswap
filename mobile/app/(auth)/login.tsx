import React, { useState } from 'react';
import { Text } from 'react-native';
import { Link } from 'expo-router';
import { friendlyMessage } from '../../src/api/errors';
import { useSession } from '../../src/auth/session';
import { Button } from '../../src/components/Button';
import { Screen } from '../../src/components/Screen';
import { TextField } from '../../src/components/TextField';
import { theme } from '../../src/theme';

export default function LoginScreen() {
  const { signIn } = useSession();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await signIn(email.trim(), password);
    } catch (e) {
      setError(friendlyMessage(e));
      setBusy(false);
    }
  }

  return (
    <Screen scroll>
      <Text style={{ fontFamily: theme.fonts.title, fontSize: 34, color: theme.colors.brown, marginTop: 40 }}>
        BookSwap
      </Text>
      <Text style={{ color: theme.colors.muted, marginTop: 4 }}>Trade the books you've finished.</Text>
      <TextField label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" autoComplete="email" />
      <TextField label="Password" value={password} onChangeText={setPassword} secureTextEntry autoComplete="password" />
      {error ? <Text style={{ color: theme.colors.danger, marginTop: 10 }}>{error}</Text> : null}
      <Button title="Sign in" onPress={submit} loading={busy} disabled={!email.trim() || !password} />
      <Link href="/register" style={{ color: theme.colors.accent, marginTop: 18, textAlign: 'center' }}>
        New here? Create an account
      </Link>
    </Screen>
  );
}
