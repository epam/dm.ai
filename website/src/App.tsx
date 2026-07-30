import { Nav } from './components/Nav';
import { Hero } from './components/Hero';
import { Benefits } from './components/Benefits';
import { Problem } from './components/Problem';
import { Solution } from './components/Solution';
import { Numbers } from './components/Numbers';
import { Architecture } from './components/Architecture';
import { CodeSample } from './components/CodeSample';
import { UsagePaths } from './components/UsagePaths';
import { Integrations } from './components/Integrations';
import { Trust } from './components/Trust';
import { Faq } from './components/Faq';
import { Cta } from './components/Cta';
import { Footer } from './components/Footer';

function App() {
  return (
    <>
      <Nav />
      <main>
        <Hero />
        <Benefits />
        <Problem />
        <Solution />
        <Numbers />
        <Architecture />
        <CodeSample />
        <UsagePaths />
        <Integrations />
        <Trust />
        <Faq />
        <Cta />
      </main>
      <Footer />
    </>
  );
}

export default App;
