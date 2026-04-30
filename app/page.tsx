'use client';
import { useState } from 'react';

export default function CodeStackDashboard() {
  const [task, setTask] = useState('');
  const [files, setFiles] = useState('package.json'); // Default file to analyze
  const [loading, setLoading] = useState(false);

  const runTask = async () => {
    setLoading(true);
    const res = await fetch('/api/dispatch', {
      method: 'POST',
      body: JSON.stringify({
        task,
        files: files.split(',').map(f => f.trim()),
      }),
    });
    const data = await res.json();
    alert(data.status || "Check GitHub Actions tab!");
    setLoading(false);
  };

  return (
    <div style={{ padding: '40px', fontFamily: 'sans-serif' }}>
      <h1>🚀 CodeStack Control Center</h1>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', maxWidth: '500px' }}>
        <label>Files to Contextualize (comma separated):</label>
        <input 
          value={files} 
          onChange={(e) => setFiles(e.target.value)}
          placeholder="e.g., app/page.tsx, lib/utils.ts"
        />
        
        <label>What should the AI do?</label>
        <textarea 
          rows={5}
          value={task}
          onChange={(e) => setTask(e.target.value)}
          placeholder="e.g., Rewrite the styling in page.tsx to use Tailwind."
        />
        
        <button 
          onClick={runTask} 
          disabled={loading}
          style={{ padding: '10px', cursor: 'pointer', background: '#0070f3', color: 'white', border: 'none', borderRadius: '5px' }}
        >
          {loading ? 'Dispatching Agent...' : 'Execute Task'}
        </button>
      </div>
    </div>
  );
}
