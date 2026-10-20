import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteMe, getMe, updateMe } from '../../../src/api/endpoints/profile';
import { friendlyMessage } from '../../../src/api/errors';
import { useApi, useSession } from '../../../src/auth/session';
import { Button } from '../../../src/components/Button';
import { ErrorState } from '../../../src/components/ErrorState';
import { Screen } from '../../../src/components/Screen';
import { TextField } from '../../../src/components/TextField';
import { ProfileErrors, validateProfile } from '../../../src/logic/profileForm';
import { theme } from '../../../src/theme';

export default function ProfileScreen() {
  const api = useApi();
  const { signOut } = useSession();
  const qc = useQueryClient();
  const me = useQuery({ queryKey: ['me'], queryFn: () => getMe(api) });

  const [displayName, setDisplayName] = useState('');
  const [city, setCity] = useState('');
  const [postalCode, setPostalCode] = useState('');
  const [contactNote, setContactNote] = useState('');
  const [errors, setErrors] = useState<ProfileErrors>({});
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  const [deleting, setDeleting] = useState(false);
  const [password, setPassword] = useState('');
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const filled = useRef(false);
  useEffect(() => {
    if (me.data && !filled.current) {
      filled.current = true;
      setDisplayName(me.data.displayName ?? '');
      setCity(me.data.city ?? '');
      setPostalCode(me.data.postalCode ?? '');
      setContactNote(me.data.contactNote ?? '');
    }
  }, [me.data]);

  async function save() {
    const values = { displayName, city, postalCode, contactNote };
    const found = validateProfile(values);
    setErrors(found);
    setSaved(false);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    setSaveError(null);
    try {
      const updated = await updateMe(api, {
        displayName: displayName.trim(), city: city.trim(), postalCode: postalCode.trim(), contactNote: contactNote.trim(),
      });
      qc.setQueryData(['me'], updated);
      setSaved(true);
    } catch (e) {
      setSaveError(friendlyMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function deleteAccount() {
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await deleteMe(api, password);
      await signOut();
    } catch (e) {
      setDeleteError(friendlyMessage(e));
      setDeleteBusy(false);
    }
  }

  if (me.isLoading) return <Screen><ActivityIndicator color={theme.colors.accent} style={{ marginTop: 40 }} /></Screen>;
  if (me.isError || !me.data) return <Screen><ErrorState error={me.error} onRetry={() => me.refetch()} /></Screen>;

  return (
    <Screen scroll>
      {!me.data.profileComplete ? (
        <View style={{ backgroundColor: theme.colors.paper, borderColor: theme.colors.accent, borderWidth: 1, borderRadius: theme.radius.card, padding: 12, marginBottom: 8 }}>
          <Text style={{ color: theme.colors.brown }}>
            Complete your profile (name, city and postal code) so you can list books and swap.
          </Text>
        </View>
      ) : null}
      <Text style={{ color: theme.colors.muted }}>Signed in as</Text>
      <Text style={{ color: theme.colors.ink, fontSize: 16 }}>{me.data.email}</Text>

      <TextField label="Display name" value={displayName} onChangeText={setDisplayName} maxLength={60} />
      {errors.displayName ? <Text style={{ color: theme.colors.danger }}>{errors.displayName}</Text> : null}
      <TextField label="City" value={city} onChangeText={setCity} maxLength={100} />
      {errors.city ? <Text style={{ color: theme.colors.danger }}>{errors.city}</Text> : null}
      <TextField label="Postal code" value={postalCode} onChangeText={setPostalCode} maxLength={20} autoCapitalize="characters" />
      {errors.postalCode ? <Text style={{ color: theme.colors.danger }}>{errors.postalCode}</Text> : null}
      <TextField label="Contact note (optional)" value={contactNote} onChangeText={setContactNote} multiline />
      <Text style={{ color: theme.colors.muted, marginTop: 4 }}>Shown only to the other person once you both accept a swap.</Text>
      {errors.contactNote ? <Text style={{ color: theme.colors.danger }}>{errors.contactNote}</Text> : null}
      {saveError ? <Text style={{ color: theme.colors.danger, marginTop: 8 }}>{saveError}</Text> : null}
      {saved ? <Text style={{ color: theme.colors.success, marginTop: 8 }}>Saved.</Text> : null}
      <Button title="Save profile" loading={busy} onPress={save} />

      <View style={{ height: 1, backgroundColor: theme.colors.border, marginVertical: 24 }} />
      <Button title="Log out" variant="secondary" onPress={() => signOut()} />

      {!deleting ? (
        <Button title="Delete account" variant="danger" onPress={() => setDeleting(true)} />
      ) : (
        <View style={{ marginTop: 16 }}>
          <Text style={{ color: theme.colors.danger }}>
            This removes your books and erases your personal details. Other people keep their exchange history, with you shown as "Deleted user".
          </Text>
          <TextField label="Current password" value={password} onChangeText={setPassword} secureTextEntry />
          {deleteError ? <Text style={{ color: theme.colors.danger, marginTop: 8 }}>{deleteError}</Text> : null}
          <Button title="Delete my account" variant="danger" loading={deleteBusy} disabled={!password} onPress={deleteAccount} />
          <Button title="Never mind" variant="secondary" onPress={() => { setDeleting(false); setPassword(''); setDeleteError(null); }} />
        </View>
      )}
    </Screen>
  );
}
