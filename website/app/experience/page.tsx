import type { Metadata } from 'next';
import './experience.css';

export const metadata: Metadata = {
  title: 'ONE — Global Broadcast Experience',
  description: 'Pull back from the only live screen and see one message broadcast everywhere at once.',
};

export default function ExperiencePage() {
  return (
    <main className="experience-route">
      <iframe
        className="experience-frame"
        src="/one-broadcast-concept.html"
        title="ONE global broadcast experience"
        allow="fullscreen"
      />
      <noscript>
        <p className="experience-fallback">
          This experience needs JavaScript. <a href="/">Return to ONE</a>.
        </p>
      </noscript>
    </main>
  );
}
