import { Eye, EyeOff, LogIn } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { PageHeader } from '../layout/PageHeader';
import { StateMessage } from '../state/StateMessage';

type LoginPageProps = { onAuthenticated: (token: string) => void; };
type LoginResponse = { token: string; };

const manualSections = [
  {
    title: 'Finalidade',
    content: 'Permitir o acesso seguro ao KRINO e apresentar, antes da autenticação, como a plataforma conecta a rotina escolar à decisão pedagógica em uma única base de dados da Rede.',
  },
  {
    title: 'Campos',
    content: 'Usuário identifica a conta. Senha confirma a credencial de acesso. O botão de visualização permite conferir a senha digitada.',
  },
  {
    title: 'Botões e ações',
    content: 'Entrar autentica a conta. O botão ao lado da senha alterna entre mostrar e ocultar os caracteres.',
  },
  {
    title: 'Regras e permissões',
    content: 'Somente contas ativas podem entrar. Depois do login, cada ação continua sendo validada pelo backend conforme perfil, permissão e escopo.',
  },
  {
    title: 'Fluxo principal',
    content: 'Informe usuário e senha e selecione Entrar. Em caso de sucesso, o sistema abre a área permitida para a conta.',
  },
  {
    title: 'Mensagens e estados',
    content: 'Durante a autenticação, o botão informa que a entrada está em andamento. Credenciais inválidas são informadas sem revelar se o usuário ou a senha está incorreto. Falhas de comunicação orientam uma nova tentativa.',
  },
];

export function LoginPage({ onAuthenticated }: LoginPageProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await apiRequest<LoginResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      });
      onAuthenticated(response.token);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível entrar. Tente novamente.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="auth-page">
      <PageHeader
        eyebrow="KRINO"
        title="Da rotina escolar à decisão pedagógica, em uma única plataforma."
        description="Secretaria, Diário de Classe, Avaliações em Rede, Monitoramento e comunicação conectados pelos mesmos dados da Rede."
        manualScreenName="Entrar"
        manualSections={manualSections}
      />
      <section className="auth-card" aria-labelledby="login-title">
        <h2 id="login-title">Entrar</h2>
        <form className="form-stack" onSubmit={submit}>
          <TextField
            name="username"
            label="Usuário"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            required
          />
          <div className="password-field">
            <TextField
              name="password"
              label="Senha"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
            <button
              className="password-field__toggle"
              type="button"
              aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
              title={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
              onClick={() => setShowPassword((value) => !value)}
            >
              {showPassword ? <EyeOff aria-hidden="true" size={20} /> : <Eye aria-hidden="true" size={20} />}
            </button>
          </div>
          {error ? <StateMessage kind="error" title="Não foi possível entrar" message={error} /> : null}
          <Button type="submit" variant="primary" disabled={loading}>
            <LogIn aria-hidden="true" size={18} />
            {loading ? 'Entrando...' : 'Entrar'}
          </Button>
        </form>
      </section>
    </main>
  );
}
