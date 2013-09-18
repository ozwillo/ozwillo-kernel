node 'oasis-demo.atolcd.priv' {
  class { '::system_tools::server::box' :
    admin_mail => 'jpo@atolcd.com',
  }

}

