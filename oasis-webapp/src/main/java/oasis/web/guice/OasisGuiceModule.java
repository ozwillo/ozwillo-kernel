package oasis.web.guice;

import com.google.inject.AbstractModule;

import oasis.model.accounts.AccountRepository;
import oasis.model.applications.ApplicationRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.services.accounts.JongoAccountRepository;
import oasis.services.applications.DummyApplicationRepository;
import oasis.services.directory.DummyDirectoryRepository;

public class OasisGuiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(AccountRepository.class).to(JongoAccountRepository.class);
    bind(DirectoryRepository.class).to(DummyDirectoryRepository.class);
    bind(ApplicationRepository.class).to(DummyApplicationRepository.class);
  }
}
