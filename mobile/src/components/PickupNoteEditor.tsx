import React, { useState } from 'react';
import { Button } from './Button';
import { TextField } from './TextField';

type Props = { initial: string | null; busy: boolean; onSave: (note: string) => void };

/** Where and when to meet; saving an empty box clears the note. */
export function PickupNoteEditor({ initial, busy, onSave }: Props) {
  const [note, setNote] = useState(initial ?? '');
  return (
    <>
      <TextField label="Where and when to meet" value={note} onChangeText={setNote} maxLength={500} multiline />
      <Button title="Save note" variant="secondary" loading={busy} onPress={() => onSave(note.trim())} />
    </>
  );
}
