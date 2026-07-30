import { Nav } from './components/Nav';
import { Hero } from './components/Hero';
import { Problem } from './components/Problem';
import { Solution } from './components/Solution';
import { Numbers } from './components/Numbers';
import { Architecture } from './components/Architecture';
import { CodeSample } from './components/CodeSample';
import { UsagePaths } from './components/UsagePaths';
import { Integrations } from './components/Integrations';
import { Cta } from './components/Cta';
import { Footer } from './components/Footer';

function App() {
  return (
    <>
      <Nav />
      <main>
        <Hero />
        <Problem />
        <Solution />
        <Numbers />
        <Architecture />
        <CodeSample />
        <UsagePaths />
        <Integrations />
        <Cta />
      </main>
      <Footer />
    </>
  );
}

export default App;
