type Tab<T extends string> = { value: T; label: string; };
type SegmentedTabsProps<T extends string> = { label: string; tabs: Tab<T>[]; value: T; onChange: (value: T) => void; };

export function SegmentedTabs<T extends string>({ label, tabs, value, onChange }: SegmentedTabsProps<T>) {
  return <nav className="segmented segmented--wrap" aria-label={label}>{tabs.map((tab) => <button key={tab.value} className={value === tab.value ? 'segmented__item segmented__item--active' : 'segmented__item'} type="button" onClick={() => onChange(tab.value)}>{tab.label}</button>)}</nav>;
}
