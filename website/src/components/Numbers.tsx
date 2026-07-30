const STATS = [
  { value: '320+', label: 'CLI tools' },
  { value: '20+', label: 'integrations' },
  { value: '4', label: 'usage paths' },
  { value: 'Apache 2.0', label: 'license' },
];

const BARS = [
  { name: 'Jira', count: 69 },
  { name: 'GitHub', count: 35 },
  { name: 'Teams', count: 31 },
  { name: 'Azure DevOps', count: 38 },
  { name: 'GitLab', count: 30 },
  { name: 'Bitrise', count: 23 },
  { name: 'Figma', count: 22 },
  { name: 'Confluence', count: 19 },
  { name: 'TestRail', count: 17 },
  { name: 'Jenkins', count: 5 },
];

const MAX = Math.max(...BARS.map((b) => b.count));

export function Numbers() {
  return (
    <section className="section section--bordered" id="numbers">
      <div className="container">
        <p className="eyebrow">By the numbers</p>
        <h2>
          <span className="gradient-word">320+</span> CLI tools. 20+ integrations.
        </h2>
        <p className="section__lede">
          Every number below is a verified count from the generated per-integration tool reference —
          and the list keeps growing.
        </p>

        <div className="stats">
          {STATS.map((s) => (
            <div className="stats__item" key={s.label}>
              <div className="stats__value">{s.value}</div>
              <div className="stats__label">{s.label}</div>
            </div>
          ))}
        </div>

        <div className="bars">
          {BARS.map((b) => (
            <div className="bars__row" key={b.name}>
              <span className="bars__name">{b.name}</span>
              <div className="bars__track">
                <div className="bars__fill" style={{ width: `${(b.count / MAX) * 100}%` }} />
              </div>
              <span className="bars__count">{b.count}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
