import React from 'react';
import { Pressable, Text, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { conditionGrades } from '../api/endpoints/copies';
import type { ConditionGrade } from '../api/types';
import { useApi } from '../auth/session';
import { theme } from '../theme';

type Props = { value: ConditionGrade; onChange: (g: ConditionGrade) => void; disabled?: boolean };

/** Condition chips; the selected grade's criteria are shown underneath. Labels come from the backend. */
export function GradePicker({ value, onChange, disabled = false }: Props) {
  const api = useApi();
  const { data: grades = [] } = useQuery({ queryKey: ['grades'], queryFn: () => conditionGrades(api), staleTime: Infinity });
  const selected = grades.find((g) => g.code === value);
  return (
    <View>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', marginTop: 6 }}>
        {grades.map((g) => {
          const on = g.code === value;
          return (
            <Pressable
              key={g.code}
              accessibilityRole="button"
              accessibilityLabel={g.label}
              accessibilityState={{ selected: on, disabled }}
              disabled={disabled}
              onPress={() => onChange(g.code)}
              style={{
                backgroundColor: on ? theme.colors.brown : 'transparent',
                borderColor: theme.colors.brown,
                borderWidth: 1,
                borderRadius: 20,
                paddingHorizontal: 12,
                paddingVertical: 6,
                marginRight: 8,
                marginBottom: 8,
              }}
            >
              <Text style={{ color: on ? theme.colors.paper : theme.colors.brown }}>{g.label}</Text>
            </Pressable>
          );
        })}
      </View>
      {selected ? <Text style={{ color: theme.colors.muted }}>{selected.criteria}</Text> : null}
    </View>
  );
}
