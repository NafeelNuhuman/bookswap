import { Text, View } from 'react-native';
import { theme } from '../src/theme';

export default function Index() {
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: theme.colors.cream }}>
      <Text style={{ fontFamily: theme.fonts.title, fontSize: 28, color: theme.colors.brown }}>BookSwap</Text>
    </View>
  );
}
