import React, { useState } from 'react';
import { Text, View } from 'react-native';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { addCopy, lookupBook, NewCopy } from '../../../src/api/endpoints/copies';
import { ApiError, friendlyMessage } from '../../../src/api/errors';
import type { BookLookup, ConditionGrade } from '../../../src/api/types';
import { useApi } from '../../../src/auth/session';
import { BarcodeScanner } from '../../../src/components/BarcodeScanner';
import { BookCover } from '../../../src/components/BookCover';
import { Button } from '../../../src/components/Button';
import { GradePicker } from '../../../src/components/GradePicker';
import { Screen } from '../../../src/components/Screen';
import { TextField } from '../../../src/components/TextField';
import { normalizeIsbn } from '../../../src/logic/isbn';
import { theme } from '../../../src/theme';

export default function AddBookScreen() {
  const api = useApi();
  const router = useRouter();
  const qc = useQueryClient();

  const [isbnText, setIsbnText] = useState('');
  const [scanning, setScanning] = useState(false);
  const [scanMessage, setScanMessage] = useState<string | null>(null);
  const [book, setBook] = useState<BookLookup | null>(null);
  const [manualIsbn, setManualIsbn] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  const [author, setAuthor] = useState('');
  const [grade, setGrade] = useState<ConditionGrade>('GOOD');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function lookup(raw: string) {
    setError(null);
    const isbn = normalizeIsbn(raw);
    if (!isbn) {
      setError("That isn't a valid ISBN.");
      return;
    }
    setBusy(true);
    try {
      setBook(await lookupBook(api, isbn));
      setManualIsbn(null);
    } catch (e) {
      setBook(null);
      if (e instanceof ApiError && (e.code === 'BOOK_NOT_FOUND' || e.code === 'CATALOG_UNAVAILABLE')) {
        setManualIsbn(isbn);
      }
      setError(friendlyMessage(e));
    } finally {
      setBusy(false);
    }
  }

  function onScan(data: string) {
    const isbn = normalizeIsbn(data);
    if (!isbn) {
      setScanMessage("That doesn't look like a book barcode.");
      return;
    }
    setScanMessage(null);
    setScanning(false);
    setIsbnText(isbn);
    lookup(isbn);
  }

  async function save() {
    const isbn = book?.isbn ?? manualIsbn;
    if (!isbn) return;
    const body: NewCopy = { isbn, grade };
    if (!book) {
      body.title = title.trim();
      if (author.trim()) body.author = author.trim();
    }
    setBusy(true);
    setError(null);
    try {
      await addCopy(api, body);
      qc.invalidateQueries({ queryKey: ['copies', 'list'] });
      router.back();
    } catch (e) {
      setError(friendlyMessage(e));
      setBusy(false);
    }
  }

  const readyToSave = !!book || (!!manualIsbn && title.trim().length > 0);

  return (
    <Screen scroll>
      <TextField label="ISBN" value={isbnText} onChangeText={setIsbnText} keyboardType="numbers-and-punctuation" placeholder="978-…" />
      <Button title="Look up" onPress={() => lookup(isbnText)} loading={busy && !readyToSave} disabled={!isbnText.trim()} />
      <Button
        title={scanning ? 'Stop scanning' : 'Scan barcode'}
        variant="secondary"
        onPress={() => { setScanMessage(null); setScanning((s) => !s); }}
      />
      {scanning ? <BarcodeScanner onScan={onScan} message={scanMessage} /> : null}

      {error ? <Text style={{ color: theme.colors.danger, marginTop: 12 }}>{error}</Text> : null}

      {book ? (
        <View style={{ marginTop: 16 }}>
          <View style={{ width: '50%', alignSelf: 'center' }}>
            <BookCover coverUrl={book.coverUrl} title={book.title} height={220} />
          </View>
          <Text style={{ fontFamily: theme.fonts.title, fontSize: 22, color: theme.colors.ink, marginTop: 10 }}>{book.title}</Text>
          {book.author ? <Text style={{ color: theme.colors.muted }}>{book.author}</Text> : null}
        </View>
      ) : null}

      {manualIsbn ? (
        <View style={{ marginTop: 8 }}>
          <TextField label="Title" value={title} onChangeText={setTitle} maxLength={500} />
          <TextField label="Author" value={author} onChangeText={setAuthor} maxLength={500} />
        </View>
      ) : null}

      {readyToSave ? (
        <View style={{ marginTop: 16 }}>
          <Text style={{ color: theme.colors.brown, fontWeight: '600' }}>Condition</Text>
          <GradePicker value={grade} onChange={setGrade} />
          <Button title="Add to my library" onPress={save} loading={busy} />
        </View>
      ) : null}
    </Screen>
  );
}
