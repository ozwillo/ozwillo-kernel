class oasis (
  $elasticsearch_host = 'localhost',
  $package_url        = false,
) {

  if ! defined(Package['openjdk-7-jdk']) {
    package { 'openjdk-7-jdk':
      ensure => present,
    }
  }

  class {'::fluentd': }
  fluentd::in::http {'all':}
  fluentd::out::elasticsearch {'default':
    host            => $elasticsearch_host,
    logstash_format => true,
  }

  if $package_url {
    # create a local copy of the package
    $filename_array = split($package_url, '/')
    $base_filename = $filename_array[-1]
    $tmp_source = "/tmp/${base_filename}"

    file { $tmp_source:
      source => $package_url,
      owner  => 'root',
      group  => 'root',
      backup => false,
    }

    package { 'oasis':
      ensure   => latest,
      provider => 'dpkg',
      source   => $tmp_source,
      require  => [Package['openjdk-7-jdk'], File[$tmp_source]],
      notify   => Service['oasis'],
    }

    service {'oasis':
      ensure => running,
      enable => true,
    }
  }
}

