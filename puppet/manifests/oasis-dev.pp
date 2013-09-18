node 'oasis-demo.atolcd.priv' {
  class { '::system_tools::server::dev' :
    admin_mail => 'jpo@atolcd.com',
  }

  include ::system_tools::adminusers::bma
  include ::system_tools::adminusers::jpo
  include ::system_tools::adminusers::tbr


}

