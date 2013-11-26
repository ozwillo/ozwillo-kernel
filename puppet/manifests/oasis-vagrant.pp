node 'oasis-box.atolcd.priv' {
  class { '::system_tools::server::box' :
    admin_mail => 'jpo@atolcd.com',
  }

  class { '::elasticsearch':
    package_url       => download_file('files', 'elasticsearch/elasticsearch-0.90.7.deb', 'https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-0.90.7.deb'),
    java_install      => true,
    java_package      => 'openjdk-7-jdk',
    config => { # see https://github.com/elasticsearch/elasticsearch/blob/master/config/elasticsearch.yml
      'cluster.name'  => 'OasisBoxES',
      'node.name'     => 'OasisBoxES',
    }
  }

  class { '::mongodb':
    no_prealloc => true,
  }

  class {'::oasis':
    package_url               => '/project/oasis-dist/target/oasis-dist_0.3.0+SNAPSHOT_all.deb',
    elasticsearch_cluster     => 'OasisBoxES',
    require                   => Class['mongodb', 'elasticsearch'],
  }
}

