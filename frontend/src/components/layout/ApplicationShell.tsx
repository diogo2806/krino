import { Menu, UserRound, X } from 'lucide-react';
import { useId, useState, type ReactNode } from 'react';
import { Button } from '../button/Button';

export type ApplicationShellNavigationItem = {
  id: string;
  label: string;
  icon: ReactNode;
  active: boolean;
  onSelect: () => void;
};

type ApplicationShellProps = {
  contextLabel: string;
  userName: string;
  navigationItems: ApplicationShellNavigationItem[];
  onLogout: () => void;
  children: ReactNode;
};

export function ApplicationShell({ contextLabel, userName, navigationItems, onLogout, children }: ApplicationShellProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const navigationId = useId();

  return (
    <>
      <header className="app-shell-header">
        <button
          className="icon-button icon-button--only app-shell-header__menu-button"
          type="button"
          aria-label={menuOpen ? 'Fechar navegação principal' : 'Abrir navegação principal'}
          aria-expanded={menuOpen}
          aria-controls={navigationId}
          title={menuOpen ? 'Fechar navegação' : 'Abrir navegação'}
          onClick={() => setMenuOpen((current) => !current)}
        >
          {menuOpen ? <X aria-hidden="true" size={20} /> : <Menu aria-hidden="true" size={20} />}
        </button>
        <div className="app-shell-header__brand">
          <strong>KRINO</strong>
          <span>{contextLabel}</span>
        </div>
        <div className="app-shell-header__user" title={`Usuário: ${userName}`}>
          <UserRound aria-hidden="true" size={18} />
          <span>{userName}</span>
        </div>
      </header>

      <nav id={navigationId} className={menuOpen ? 'workspace-nav workspace-nav--open' : 'workspace-nav'} aria-label="Módulos do KRINO">
        <div className="workspace-nav__modules">
          {navigationItems.map((item) => (
            <button
              key={item.id}
              className={item.active ? 'workspace-nav__item workspace-nav__item--active' : 'workspace-nav__item'}
              type="button"
              aria-current={item.active ? 'page' : undefined}
              onClick={() => {
                item.onSelect();
                setMenuOpen(false);
              }}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </div>
        <Button
          type="button"
          variant="ghost"
          onClick={() => {
            setMenuOpen(false);
            onLogout();
          }}
        >
          Sair
        </Button>
      </nav>

      {children}
    </>
  );
}
