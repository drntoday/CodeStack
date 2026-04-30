export const metadata = {
  title: 'CodeStack DIY',
  description: 'My Personal AI Codex',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body style={{ margin: 0, backgroundColor: '#f4f4f9' }}>
        {children}
      </body>
    </html>
  )
}
