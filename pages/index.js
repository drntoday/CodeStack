import { useState } from 'react';

export default function Home() {
  const [prompt, setPrompt] = useState('');
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);

  const generate = async () => {
    setLoading(true);
    setCode('');
    try {
      const res = await fetch('/api/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt }),
      });
      const data = await res.json();
      setCode(data.code || 'No code generated.');
    } catch (err) {
      setCode('Error: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 720, margin: '2rem auto', fontFamily: 'sans-serif' }}>
      <h1>🧠 Code Stack</h1>
      <textarea
        rows={4}
        value={prompt}
        onChange={(e) => setPrompt(e.target.value)}
        placeholder="e.g., Python function to merge two sorted lists"
        style={{ width: '100%', padding: '0.5rem', marginBottom: '1rem' }}
      />
      <button onClick={generate} disabled={loading} style={{ padding: '0.5rem 1.5rem' }}>
        {loading ? 'Generating...' : 'Generate Code'}
      </button>
      {code && (
        <div style={{ marginTop: '2rem' }}>
          <h2>Result</h2>
          <pre style={{ background: '#f4f4f4', padding: '1rem', overflowX: 'auto' }}>
            <code>{code}</code>
          </pre>
        </div>
      )}
    </div>
  );
}
