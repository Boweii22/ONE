import type { Metadata } from 'next';
import { Geist, Geist_Mono, Anton } from 'next/font/google';
import './globals.css';
import './takeover.css';

const geistSans = Geist({ variable: '--font-geist-sans', subsets: ['latin'] });
const geistMono = Geist_Mono({ variable: '--font-geist-mono', subsets: ['latin'] });
const anton = Anton({ variable: '--font-display', subsets: ['latin'], weight: '400' });

export const metadata: Metadata = {
  metadataBase: new URL('https://oneis.live'),
  title: 'ONE — Not another feed. Your turn.',
  description: 'One shared screen. One message at a time. Take it for a laugh. Take it to say something. Available now on Google Play.',
  icons: {
    icon: '/favicon.svg',
    apple: '/one-app-icon.png',
  },
  openGraph: {
    title: 'ONE — Not another feed. Your turn.',
    description: 'One shared screen. One message at a time. Available now on Google Play.',
    type: 'website',
    images: [{ url: '/og.png', width: 1200, height: 630, alt: 'ONE — Take the only live screen.' }],
  },
  twitter: {
    card: 'summary_large_image',
    title: 'ONE — Not another feed. Your turn.',
    description: 'One shared screen. One message at a time. Available now on Google Play.',
    images: ['/og.png'],
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body className={`${geistSans.variable} ${geistMono.variable} ${anton.variable} antialiased`}>{children}</body></html>;
}
