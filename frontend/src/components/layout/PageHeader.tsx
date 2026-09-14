import type { ReactNode } from 'react';
import { ScreenManual, type ManualSection } from '../manual/ScreenManual';

type PageHeaderProps = {
  eyebrow?: string;
  title: string;
  description?: string;
  manualSections: ManualSection[];
  manualScreenName?: string;
  actions?: ReactNode;
};

export function PageHeader({ eyebrow, title, description, manualSections, manualScreenName, actions }: PageHeaderProps) {
  return (
    <header className="page-header">
      <div className="page-header__copy">
        {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
        <h1>{title}</h1>
        {description ? <p className="page-header__description">{description}</p> : null}
      </div>
      <div className="page-header__actions">
        {actions}
        <ScreenManual screenName={manualScreenName ?? title} sections={manualSections} />
      </div>
    </header>
  );
}
