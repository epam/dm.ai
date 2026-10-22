import { registerHooks } from 'node:module';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

registerHooks({
  load(url, context, nextLoad) {
    if (url.endsWith('?raw')) {
      const cleanUrl = url.slice(0, -4);
      const content = readFileSync(fileURLToPath(cleanUrl), 'utf8');
      return {
        format: 'module',
        shortCircuit: true,
        source: `export default ${JSON.stringify(content)};\n`,
      };
    }
    return nextLoad(url, context);
  },
});
