node 'atolcd-oasis-demo.hosting.atolcd.priv' {
  class { '::system_tools::server::demo' :
    admin_mail => 'jpo@atolcd.com',
  }

  include ::system_tools::adminusers::bma
  include ::system_tools::adminusers::jpo
  include ::system_tools::adminusers::tbr
  include ::system_tools::adminusers::apo
  include ::system_tools::adminusers::xca

  include ::oasis::params

  $version = 'LATEST'
  $url = "http://nexus.atolcd.priv/service/local/artifact/maven/content?g=${::oasis::params::group_id}&a=${::oasis::params::artifact_id}&v=${version}&r=releases&p=deb"

  class {'::oasis':
    elasticsearch_host => 'atolcd-elasticsearch-1.hosting.atolcd.priv',
    package_url        => download_file('files', "oasis/${::oasis::params::artifact_id}-${version}.deb", $url),
  }
}
