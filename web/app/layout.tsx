import type { Metadata } from 'next';
import { Geist, Geist_Mono, Anton } from 'next/font/google';
import './globals.css';
import './takeover.css';

const geistSans = Geist({ variable: '--font-geist-sans', subsets: ['latin'] });
const geistMono = Geist_Mono({ variable: '--font-geist-mono', subsets: ['latin'] });
const anton = Anton({ variable: '--font-display', subsets: ['latin'], weight: '400' });

export const metadata: Metadata = {
  title: 'ONE — The Only Live Screen',
  description: 'One person owns it. Everyone can see it. Anyone in the Android app can steal it.',
  openGraph: {
    title: 'ONE — The Only Live Screen',
    description: 'One owner. One message. Take it.',
    type: 'website',
    images: [{ url: '/og.png', width: 1200, height: 630, alt: 'ONE — Take the only live screen.' }],
  },
  twitter: {
    card: 'summary_large_image',
    title: 'ONE — The Only Live Screen',
    description: 'One owner. One message. Take it.',
    images: ['/og.png'],
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body className={`${geistSans.variable} ${geistMono.variable} ${anton.variable} antialiased`}>{children}</body></html>;
}
