import { configure } from '@testing-library/react-native';

// findBy*/waitFor default to 1s, which a slow CI runner can exceed.
configure({ asyncUtilTimeout: 5000 });
