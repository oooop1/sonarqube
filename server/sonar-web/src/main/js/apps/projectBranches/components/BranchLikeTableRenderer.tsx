/*
 * SonarQube
 * Copyright (C) 2009-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import * as React from 'react';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { getBranchLikeKey } from '../../../helpers/branches';
import BranchLikeRowRenderer from './BranchLikeRowRenderer';

export interface BranchLikeTableRendererProps {
  component: T.Component;
  tableTitle: string;
  branchLikes: T.BranchLike[];
  onDelete: (branchLike: T.BranchLike) => void;
  onRename: (branchLike: T.BranchLike) => void;
}

export function BranchLikeTableRenderer(props: BranchLikeTableRendererProps) {
  const { branchLikes, component, onDelete, onRename, tableTitle } = props;

  return (
    <div className="boxed-group boxed-group-inner">
      <table className="data zebra zebra-hover">
        <thead>
          <tr>
            <th>{tableTitle}</th>
            <th className="thin nowrap">{translate('status')}</th>
            <th className="thin nowrap text-right big-spacer-left">
              {translate('project_branch_pull_request.last_analysis_date')}
            </th>
            <th className="thin nowrap text-right">{translate('actions')}</th>
          </tr>
        </thead>
        <tbody>
          {branchLikes.map(branchLike => (
            <BranchLikeRowRenderer
              branchLike={branchLike}
              component={component}
              key={getBranchLikeKey(branchLike)}
              onDelete={() => onDelete(branchLike)}
              onRename={() => onRename(branchLike)}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default React.memo(BranchLikeTableRenderer);