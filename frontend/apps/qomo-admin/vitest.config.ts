import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html'],
      thresholds: {
        lines: 70,
        statements: 70,
        branches: 70,
        functions: 70,
      },
      exclude: [
        'src/main.ts',
        '**/*.config.ts',
        'src/environments/**',
        '**/*.dto.ts',
        '**/*.model.ts',
        '**/*.entity.ts',
        '**/*.interface.ts',
        '**/*.mock.ts',
        '**/index.ts',
      ]
    }
  }
});