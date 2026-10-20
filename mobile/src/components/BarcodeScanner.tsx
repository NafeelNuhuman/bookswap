import React, { useRef } from 'react';
import { Text, View } from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { theme } from '../theme';
import { Button } from './Button';

type Props = { onScan: (data: string) => void; message?: string | null };

/** Camera view that reports EAN-13 barcodes (ISBNs); asks for permission first. */
export function BarcodeScanner({ onScan, message }: Props) {
  const [permission, requestPermission] = useCameraPermissions();
  const last = useRef<string | null>(null);

  if (!permission) return null;
  if (!permission.granted) {
    return (
      <View style={{ marginTop: 12 }}>
        <Text style={{ color: theme.colors.ink }}>
          Camera access is off. {permission.canAskAgain ? 'Allow it to scan barcodes, or ' : 'You can switch it on in your phone settings, or '}
          type the ISBN instead.
        </Text>
        {permission.canAskAgain ? <Button title="Allow camera" onPress={() => requestPermission()} variant="secondary" /> : null}
      </View>
    );
  }
  return (
    <View style={{ marginTop: 12 }}>
      <View style={{ height: 260, borderRadius: theme.radius.card, overflow: 'hidden' }}>
        <CameraView
          style={{ flex: 1 }}
          facing="back"
          barcodeScannerSettings={{ barcodeTypes: ['ean13'] }}
          onBarcodeScanned={({ data }) => {
            if (data === last.current) return; // the camera reports the same code many times a second
            last.current = data;
            onScan(data);
          }}
        />
      </View>
      <Text style={{ color: theme.colors.muted, marginTop: 6 }}>Point the camera at the barcode on the back cover.</Text>
      {message ? <Text style={{ color: theme.colors.danger, marginTop: 4 }}>{message}</Text> : null}
    </View>
  );
}
