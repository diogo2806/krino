import type { ReactNode } from 'react';

type FilterBarProps = { children: ReactNode; actions?: ReactNode; };

export function FilterBar({ children, actions }: FilterBarProps) {
  return <div className="filter-bar"><div className="filter-bar__fields">{children}</div>{actions ? <div className="filter-bar__actions">{actions}</div> : null}</div>;
}
