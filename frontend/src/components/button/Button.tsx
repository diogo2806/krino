import type { ButtonHTMLAttributes, ReactNode } from 'react';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  children: ReactNode;
};

export function Button({ variant = 'secondary', className = '', children, ...props }: ButtonProps) {
  return <button className={`button button--${variant} ${className}`.trim()} {...props}>{children}</button>;
}
