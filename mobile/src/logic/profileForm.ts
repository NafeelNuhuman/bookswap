export type ProfileValues = { displayName: string; city: string; postalCode: string; contactNote: string };
export type ProfileErrors = Partial<Record<keyof ProfileValues, string>>;

/** Same limits as the backend (name 60, city 100, postal code 20, contact note 200). */
export function validateProfile(v: ProfileValues): ProfileErrors {
  const errors: ProfileErrors = {};
  const name = v.displayName.trim();
  const city = v.city.trim();
  const postal = v.postalCode.trim();
  const note = v.contactNote.trim();
  if (!name) errors.displayName = 'Enter your name.';
  else if (name.length > 60) errors.displayName = 'Name can be at most 60 characters.';
  if (!city) errors.city = 'Enter your city.';
  else if (city.length > 100) errors.city = 'City can be at most 100 characters.';
  if (!postal) errors.postalCode = 'Enter your postal code.';
  else if (postal.length > 20) errors.postalCode = 'Postal code can be at most 20 characters.';
  if (note.length > 200) errors.contactNote = 'Contact note can be at most 200 characters.';
  return errors;
}
